package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.RulesGenerationStatusManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRuleItem;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.*;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarFieldUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarTextUtils;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.server.search.elasticsearch.service.RulesIndexingService;
import org.metadatacenter.intelligentauthoring.valuerecommender.io.*;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class ValueRecommenderService implements IValueRecommenderService {

  private final Logger logger = LoggerFactory.getLogger(ValueRecommenderService.class);
  private RulesIndexingService rulesIndexingService;
  private static ElasticsearchQueryService esQueryService;

  public ValueRecommenderService(CedarConfig config, RulesIndexingService rulesIndexingService) {
    // Initialize configuration manager, which will provide access to the Cedar configuration
    ConfigManager.getInstance().initialize(config);
    this.rulesIndexingService = rulesIndexingService;

    // Initialize ElasticsearchQueryService
    try {
      esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
    } catch (UnknownHostException e) {
      logger.error(e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Generate association rules for the templates specified
   *
   * @param templateIds
   */
  @Override
  public void generateRules(List<String> templateIds) {
    AssociationRulesService service = new AssociationRulesService();
    try {
      // Generate rules for all the templates (with instances) in the system
      if (templateIds.isEmpty()) {
        logger.info("Generating rules for all templates in the system");
        esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
        templateIds = esQueryService.getTemplateIds();
        logger.info("Total number of templates: " + templateIds.size());
      }

      logger.info("Generating rules for the following templates: " + templateIds.toString());

      for (String templateId : templateIds) {
        RulesGenerationStatusManager.setStatus(templateId, RulesGenerationStatus.Status.PROCESSING);
        // Generate rules for the template
        logger.info("\n\n****** Generating rules for templateId: " + templateId + " ******");
        long startTime = System.currentTimeMillis();
        List<EsRule> rules = service.generateRulesForTemplate(templateId);

        // Remove previous rules for the template
        logger.info("Removing existing rules for templateId: " + templateId + " from the index");
        long removedCount = rulesIndexingService.removeRulesFromIndex(templateId);
        logger.info(removedCount + " rules removed");

        // Index the new rules in bulk in Elasticsearch
        logger.info("Indexing rules in Elasticsearch");
        esQueryService.indexRulesBulkProcessor(rules);
        logger.info("Indexing completed");

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Rules generation and indexing completed. Total execution time: " + totalTime / 1000 + " seg (" + totalTime + " ms)");
        logger.info("\n****** Finished generating rules for templateId: " + templateId + " ******");
        RulesGenerationStatusManager.setStatus(templateId, RulesGenerationStatus.Status.COMPLETED, rules.size());
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ProcessingException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public RulesGenerationStatus getRulesGenerationStatus(String templateId) throws CedarProcessingException {
    return RulesGenerationStatusManager.getStatus(templateId);
  }

  @Override
  public List<RulesGenerationStatus> getRulesGenerationStatus() {
    return RulesGenerationStatusManager.getStatus();
  }

  /**
   * This method checks if the value recommender can generate recommendations for a template or not. This call is
   * used by the Template Editor to enable or disable recommendations for a given template, before making multiple
   * (and probably more expensive) calls, one per field, to generate recommendations.
   * If templateId is provided, it checks if there are rules for that template. Otherwise, it checks if there are any
   * rules in the system and returns "true" unless the rules-index is empty. This case is useful for cross-template
   * recommendations.
   *
   * @param templateId
   * @return
   */
  @Override
  public CanGenerateRecommendationsStatus canGenerateRecommendations(String templateId) {
    long numberOfRules = esQueryService.getNumberOfRules(templateId);
    boolean canGenerateRecommendations = false;
    if (numberOfRules > 0) {
      canGenerateRecommendations = true;
    }
    return new CanGenerateRecommendationsStatus(templateId, canGenerateRecommendations);
  }

  @Override
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField,
                                          boolean strictMatch, boolean filterByRecommendationScore,
                                          boolean filterByConfidence, boolean filterBySupport, boolean useMappings,
                                          boolean includeDetails) {

    // Perform query to find the rules that match the condition
    SearchResponse rulesSearchResponse = esQueryRules(Optional.ofNullable(templateId), populatedFields, targetField,
        strictMatch,
        filterByConfidence, filterBySupport, useMappings);

    // Translate query result to EsRule objects
    List<EsRule> relevantRules = new ArrayList<>();
    for (SearchHit hit : rulesSearchResponse.getHits()) {
      try {
        EsRule rule = new ObjectMapper().readValue(hit.getSourceAsString(), EsRule.class);
        relevantRules.add(rule);
      } catch (IOException e) {
        logger.error("Error transforming SearchHit to EsRule");
        e.printStackTrace();
      }
    }

    // Calculate the recommendation score for each rule and generate a ranked list of recommended values
    List<RecommendedValue> recommendedValues = generateRecommendations(populatedFields, relevantRules,
        filterByRecommendationScore, includeDetails);

    return new Recommendation(targetField.getFieldPath(), recommendedValues);
  }

  /**
   * This method calculates the recommendation score for each value (extracted from the consequent of a rule) and
   * generates a ranked list of recommended values. Computing the recommendation scores and the final ranking
   * in Java is easy and flexible. For best performance, we could consider performing these computations
   * at the Elasticsearch level.
   * <p>
   * The recommendation score for a value is calculated according to the following expression:
   * <p>
   * recommendation_score(v) = context_matching_score(r,C) * confidence(r)
   * <p>
   * with: context_matching_score(r,C) = |antecedent(r) ∩ C| / |antecedent(r) ∪ C|
   * <p>
   * where:
   * - C is the context
   * - v is the value extracted from the consequent of the rule r
   * - adjustment_factor is a constant to calculate the value of the recommendation score based on the confidence, when
   * the context matching score is 0.
   * <p>
   * Note that when populatedFields is 0 (there is no context), the recommendation score is calculated slightly
   * different. In this case, we aggregate the support for all the different value labels and calculate the
   * recommendation score as a percentage of the total support. The resulting recommendation score will be an
   * approximation for the percentage of values with a particular label for the target field. For example, if the
   * target field has only one value for all the instances, the recommendation score will be 100%.
   *
   * @param populatedFields
   * @param rules
   * @param filterByRecommendationScore
   * @param includeDetails
   * @return
   */
  private List<RecommendedValue> generateRecommendations(List<Field> populatedFields, List<EsRule> rules,
                                                         boolean filterByRecommendationScore, boolean includeDetails) {

    List<RecommendedValue> recommendedValues = new ArrayList<>();
    RecommendedValue.RecommendationType recommendationType;

    if (populatedFields.size() > 0) {

      recommendationType = RecommendedValue.RecommendationType.CONTEXT_DEPENDENT;

      for (EsRule rule : rules) {

        double contextMatchingScore = getContextMatchingScore(populatedFields, rule.getPremise());
        double recommendationScore = contextMatchingScore * rule.getConfidence();

        if (!filterByRecommendationScore || (filterByRecommendationScore && recommendationScore >= MIN_RECOMMENDATION_SCORE)) {

          RecommendedValue value = getRecommendedValueFromRule(rule, contextMatchingScore, recommendationScore,
              includeDetails, recommendationType);

          if (!recommendedValues.contains(value)) { // avoid generation of duplicated values
            recommendedValues.add(value);
          }
        }

        // If we did not get any context-dependent recommendations, try context-dependent (recursive call)
        if (recommendedValues.size() == 0) {
          return generateRecommendations(new ArrayList<>(), rules, filterByRecommendationScore, includeDetails);
        }

      }
    }
    else { // If there are no populated fields

      recommendationType = RecommendedValue.RecommendationType.CONTEXT_INDEPENDENT;

      HashMap<String, Double> labelSupportMap = new HashMap<>();
      int totalSupport = 0;

      for (EsRule rule : rules) {
        String valueLabel = rule.getConsequence().get(0).getFieldValueLabel();
        if (!labelSupportMap.containsKey(valueLabel)) {
          labelSupportMap.put(valueLabel, rule.getSupport());
        }
        else { // update support
          double newSupport = labelSupportMap.get(valueLabel) + rule.getSupport();
          labelSupportMap.put(valueLabel, newSupport);
        }
        totalSupport += rule.getSupport();
      }

      // Calculate the recommendation score for each value based on the support
      for (EsRule rule : rules) {
        String valueLabel = rule.getConsequence().get(0).getFieldValueLabel();
        double recommendationScore = labelSupportMap.get(valueLabel) / totalSupport;

        if (!filterByRecommendationScore || (filterByRecommendationScore && recommendationScore >= MIN_RECOMMENDATION_SCORE)) {

          RecommendedValue value = getRecommendedValueFromRule(rule, 0, recommendationScore, includeDetails,
              recommendationType);

          if (!recommendedValues.contains(value)) { // avoid generation of duplicated values
            recommendedValues.add(value);
          }
        }
      }
    }

    // Sort by score
    Collections.sort(recommendedValues);

    // Keep top n elements
    if ((recommendedValues.size() > MAX_RECOMMENDATIONS) && (MAX_RECOMMENDATIONS > 1)) {
      recommendedValues = recommendedValues.subList(0, MAX_RECOMMENDATIONS - 1);
    }

    return recommendedValues;
  }

  private RecommendedValue getRecommendedValueFromRule(EsRule rule, double contextMatchingScore,
                                                       double recommendationScore,
                                                       boolean includeDetails,
                                                       RecommendedValue.RecommendationType recommendationType) {
    RecommendedValue value = null;
    if (includeDetails) {
      RecommendationDetails details = new RecommendationDetails(rule.toShortString(), contextMatchingScore,
          rule.getConfidence(),
          rule.getSupport(), recommendationType);
      value = new RecommendedValue(rule.getConsequence().get(0).getFieldValueLabel(),
          rule.getConsequence().get(0).getFieldValueType(), recommendationScore, details);
    } else {
      value = new RecommendedValue(rule.getConsequence().get(0).getFieldValueLabel(),
          rule.getConsequence().get(0).getFieldValueType(), recommendationScore);
    }
    return value;
  }

  /**
   * Context matching score:
   * context_matching_score(r,C) = |antecedent(r) ∩ C| / |antecedent(r) ∪ C|
   * <p>
   * If there is no context, it returns a predefined value (NO_CONTEXT_FACTOR)
   */
  private double getContextMatchingScore(List<Field> populatedFields, List<EsRuleItem> rulePremise) {
    int intersectionCount = 0;
    for (EsRuleItem ruleItem : rulePremise) {
      for (Field field : populatedFields) {
        if (ruleItemMatchesPopulatedField(ruleItem, field)) {
          intersectionCount++;
        }
      }
    }
    int unionCount = populatedFields.size() + rulePremise.size() - intersectionCount;
    return (double) intersectionCount / (double) unionCount;
  }

  /**
   * This method creates and executes an Elasticsearch query that:
   * - Finds all association rules that contain the populated fields as premises and the target field as consequence.
   * - The rules are ranked by how they close the query.
   *
   * @param templateId         Template identifier (optional). If it is provided, the query is limited to the rules of
   *                           a particular template. Otherwise, all the rules in the system are queried.
   * @param populatedFields    Populated fields and their values.
   * @param targetField        Target field.
   * @param strictMatch        It performs a strict search in the sense that it will only return the rules that contain
   *                           premises that exactly match the populated fields and just one consequence, which
   *                           corresponds
   *                           to the target field. If set to false, the search will be more flexible (using an
   *                           Elasticsearch SHOULD clause for the premises), so that it will match any rules whose
   *                           consequence matches the target field.
   * @param filterByConfidence Sets a minimum confidence threshold
   * @param filterBySupport    Sets a minimum support threshold
   * @param useMappings        For ontology uris, tries to match the uri with other equivalent uris
   * @return An Elasticsearch response.
   */
  private SearchResponse esQueryRules(Optional<String> templateId, List<Field> populatedFields, Field targetField,
                                      boolean strictMatch, boolean filterByConfidence, boolean filterBySupport,
                                      boolean useMappings) {

    /** Query definition **/

    BoolQueryBuilder mainBoolQuery = QueryBuilders.boolQuery();

    if (filterByConfidence) {
      RangeQueryBuilder minConfidence =
          QueryBuilders.rangeQuery(INDEX_RULE_CONFIDENCE).from(MIN_CONFIDENCE_QUERY).includeLower(true);
      mainBoolQuery = mainBoolQuery.must(minConfidence);
    }

    if (filterBySupport) {
      RangeQueryBuilder minSupport =
          QueryBuilders.rangeQuery(INDEX_RULE_SUPPORT).from(MIN_SUPPORT_QUERY).includeLower(true);
      mainBoolQuery = mainBoolQuery.must(minSupport);
    }

    // If templateId is present, the query will be limited to rules from a particular template
    if (templateId.isPresent()) {
      MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery(INDEX_TEMPLATE_ID, templateId.get());
      mainBoolQuery = mainBoolQuery.must(matchConsequenceField);
    }

    // Match number of premises
    if (populatedFields.size() > 0) {
      MatchQueryBuilder matchPremiseSize = QueryBuilders.matchQuery(INDEX_PREMISE_SIZE, populatedFields.size());
      if (strictMatch) {
        mainBoolQuery = mainBoolQuery.must(matchPremiseSize);
      } else {
        mainBoolQuery = mainBoolQuery.should(matchPremiseSize);
      }
    }

    // Match number of consequences (i.e., 1)
    MatchQueryBuilder matchConsequenceSize = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_SIZE, CONSEQUENCE_SIZE);
    mainBoolQuery = mainBoolQuery.must(matchConsequenceSize);

    // Match fields and values in premises
    for (Field field : populatedFields) {

      // Match field path
      MatchQueryBuilder matchPremiseField = QueryBuilders.matchQuery(INDEX_PREMISE_FIELD_NORMALIZED_PATH,
          CedarTextUtils.normalizePath(field.getFieldPath()));

      // Match field with other uris
      MatchQueryBuilder matchPremiseFieldOtherUris = QueryBuilders.matchQuery(INDEX_PREMISE_FIELD_TYPE_MAPPINGS,
          field.getFieldPath());

      // Match field bool query
      BoolQueryBuilder premiseFieldBoolQuery = QueryBuilders.boolQuery();
      premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseField);
      if (useMappings) {
        premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseFieldOtherUris);
      }
      premiseFieldBoolQuery = premiseFieldBoolQuery.minimumShouldMatch(1); // Logical OR

      // Get normalized value
      String fieldNormalizedValue = CedarFieldUtils.normalizeFieldValue(field);

      // Match field normalized value
      MatchQueryBuilder matchPremiseFieldNormalizedValue =
          QueryBuilders.matchQuery(INDEX_PREMISE_FIELD_NORMALIZED_VALUE, fieldNormalizedValue);

      // Match field value bool query
      BoolQueryBuilder premiseFieldValueBoolQuery = QueryBuilders.boolQuery();
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValue);
      if (useMappings) {

        // Match field normalized value with other uris
        MatchQueryBuilder matchPremiseFieldNormalizedValues =
            QueryBuilders.matchQuery(INDEX_PREMISE_FIELD_VALUE_MAPPINGS, fieldNormalizedValue);

        premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValues);
      }
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.minimumShouldMatch(1); // Logical OR

      // Premise bool query
      BoolQueryBuilder premiseBoolQuery = QueryBuilders.boolQuery();
      premiseBoolQuery = premiseBoolQuery.must(premiseFieldBoolQuery);
      premiseBoolQuery = premiseBoolQuery.must(premiseFieldValueBoolQuery);

      NestedQueryBuilder premiseNestedQuery = QueryBuilders.nestedQuery(INDEX_RULE_PREMISE, premiseBoolQuery,
          ScoreMode.Avg);

      // Add the premise query to the main query
      if (strictMatch) {
        mainBoolQuery = mainBoolQuery.must(premiseNestedQuery);
      } else {
        mainBoolQuery = mainBoolQuery.should(premiseNestedQuery);
      }
    }

    // Match target field
    MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_FIELD_NORMALIZED_PATH,
        CedarTextUtils.normalizePath(targetField.getFieldPath()));
    MatchQueryBuilder matchConsequenceFieldOtherUris = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_FIELD_TYPE_MAPPINGS,
        targetField.getFieldPath());

    // Match target field bool query
    BoolQueryBuilder consequenceFieldBoolQuery = QueryBuilders.boolQuery();
    consequenceFieldBoolQuery = consequenceFieldBoolQuery.should(matchConsequenceField);
    if (useMappings) {
      consequenceFieldBoolQuery = consequenceFieldBoolQuery.should(matchConsequenceFieldOtherUris);
    }
    consequenceFieldBoolQuery = consequenceFieldBoolQuery.minimumShouldMatch(1); // Logical OR

    NestedQueryBuilder consequenceNestedQuery = QueryBuilders.nestedQuery(INDEX_RULE_CONSEQUENCE,
        consequenceFieldBoolQuery, ScoreMode.Avg);

    // Add the consequence query to the main query
    mainBoolQuery = mainBoolQuery.must(consequenceNestedQuery);

    /**  Execute query and return search results **/
    String indexName = ConfigManager.getCedarConfig().getElasticsearchConfig().getIndexes().getRulesIndex().getName();
    SearchRequestBuilder search =
        esQueryService.getClient().prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue())
            .setQuery(mainBoolQuery).setSize(MAX_ES_RESULTS);
    //logger.info("Search query in Query DSL:\n" + search);
    SearchResponse response = search.execute().actionGet();

    return response;
  }

  private boolean ruleItemMatchesPopulatedField(EsRuleItem ruleItem, Field populatedField) {
    String populatedFieldNormalizedPath = CedarTextUtils.normalizePath(populatedField.getFieldPath());
    String populatedFieldNormalizedValue = CedarFieldUtils.normalizeFieldValue(populatedField);

    if (populatedFieldNormalizedPath.equals(ruleItem.getFieldNormalizedPath()) &&
        populatedFieldNormalizedValue.equals(ruleItem.getFieldNormalizedValue())) {
      return true;
    } else {
      return false;
    }
  }

}

