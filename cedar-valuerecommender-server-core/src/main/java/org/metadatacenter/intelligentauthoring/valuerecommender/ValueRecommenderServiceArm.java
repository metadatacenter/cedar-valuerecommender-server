package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRuleItem;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendationDetails;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarFieldUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarTextUtils;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.server.search.elasticsearch.service.RulesIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class ValueRecommenderServiceArm implements IValueRecommenderArm {

  private final Logger logger = LoggerFactory.getLogger(ValueRecommenderServiceArm.class);
  private RulesIndexingService rulesIndexingService;
  private static ElasticsearchQueryService esQueryService;

  public ValueRecommenderServiceArm(CedarConfig config, RulesIndexingService rulesIndexingService) {
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
   * Generates association rules for the templates specified
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
      } else {
        logger.info("Generating rules for the following templates: " + templateIds.toString());
      }
      for (String templateId : templateIds) {
        logger.info("Processing templateId: " + templateId);
        logger.info("Removing all rules for templateId: " + templateId + " from the index");
        long removedCount = rulesIndexingService.removeRulesFromIndex(templateId);
        logger.info(removedCount + " rules removed");
        logger.info("Generating rules for templateId: " + templateId);
        long startTime = System.currentTimeMillis();
        List<EsRule> rules = service.generateRulesForTemplate(templateId);

        logger.info("Filtering rules by number of consequences");
        List<EsRule> filteredRules =
            AssociationRulesUtils.filterRulesByNumberOfConsequences(rules, 1);
        logger.info("No. rules after filtering: " + filteredRules.size());

        // Index all the template rules in bulk in Elasticsearch
        logger.info("Indexing rules in Elasticsearch");
        esQueryService.indexRulesBulk(filteredRules);
        logger.info("Indexing completed");

        long endTime = System.currentTimeMillis();
        long totalTime = (endTime - startTime) / 1000;
        logger.info("Rules generation and indexing completed. Execution time: " + totalTime + " seg.");
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
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField,
                                          boolean strictMatch, boolean filterByRecommendationScore,
                                          boolean filterByConfidence, boolean filterBySupport, boolean useMappings,
                                          boolean includeDetails) {

    // Perform query to find the rules that match the condition
    SearchResponse rulesSearchResponse = esQueryRules(Optional.ofNullable(templateId), populatedFields, targetField, strictMatch,
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
    MatchQueryBuilder matchPremiseSize = QueryBuilders.matchQuery(INDEX_PREMISE_SIZE, populatedFields.size());
    if (strictMatch) {
      mainBoolQuery = mainBoolQuery.must(matchPremiseSize);
    } else {
      mainBoolQuery = mainBoolQuery.should(matchPremiseSize);
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
    SearchRequestBuilder search = esQueryService.getClient().prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue())
        .setQuery(mainBoolQuery).setSize(MAX_ES_RESULTS);
    //logger.info("Search query in Query DSL:\n" + search);
    SearchResponse response = search.execute().actionGet();

    return response;
  }

  /**
   * This method calculates the recommendation score for each value (extracted from the consequent of a rule) and
   * generates a ranked list of recommended values. Note that computing the recommendation scores and the final ranking
   * in Java is easy and flexible. However, for best performance, we could consider carrying out these computations
   * at the Elasticsearch level.
   *
   * The recommendation score for a value is calculated according to the following expression:
   *
   * recommendation_score(v) =
   *      {
   *        IF context_matching_score(r,C) > 0    recommendation_score(v) = context_matching_score(r,C) * confidence(r)
   *        ELSE                                  recommendation_score(v) = confidence(r) / adjustment_factor
   *      }
   *
   * with: context_matching_score(r,C) = |antecedent(r) ∩ C| / |antecedent(r) ∪ C|
   *
   * where:
   * - C is the context
   * - v is the value extracted from the consequent of the rule r
   * - adjustment_factor is a constant to calculate the value of the recommendation score based on the confidence, when
   * the recommendation score is 0.
   *
   * @param populatedFields
   * @param rules
   * @param filterByRecommendationScore
   * @param includeDetails
   * @return
   */
  private List<RecommendedValue> generateRecommendations(List<Field> populatedFields, List<EsRule> rules,
                                                         boolean filterByRecommendationScore, boolean includeDetails) {
    final double ADJUSTMENT_FACTOR = 5;
    List<RecommendedValue> recommendedValues = new ArrayList<>();

    for (EsRule rule : rules) {
      double contextMatchingScore = getContextMatchingScore(populatedFields, rule.getPremise());
      RecommendedValue.RecommendationType recommendationType = RecommendedValue.RecommendationType.CONTEXT_INDEPENDENT;

      double recommendationScore;
      if (contextMatchingScore > 0) {
        recommendationType = RecommendedValue.RecommendationType.CONTEXT_DEPENDENT;
        recommendationScore = contextMatchingScore * rule.getConfidence();
      }
      else {
        recommendationScore = rule.getConfidence() / ADJUSTMENT_FACTOR;
      }

      if (!filterByRecommendationScore || (filterByRecommendationScore && recommendationScore >= MIN_RECOMMENDATION_SCORE)) {

        RecommendedValue value = null;
        if (includeDetails) {
          RecommendationDetails details = new RecommendationDetails(rule.toShortString(), contextMatchingScore, rule.getConfidence(),
              rule.getSupport(), recommendationType);
          value = new RecommendedValue(rule.getConsequence().get(0).getFieldValueLabel(),
              rule.getConsequence().get(0).getFieldValueType(), recommendationScore, details);
        } else {
          value = new RecommendedValue(rule.getConsequence().get(0).getFieldValueLabel(),
              rule.getConsequence().get(0).getFieldValueType(), recommendationScore);
        }

        if (!recommendedValues.contains(value)) { // avoid generation of duplicated values
          recommendedValues.add(value);
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

  /**
   * Context matching score:
   * context_matching_score(r,C) = |antecedent(r) ∩ C| / |antecedent(r) ∪ C|
   */
  private double getContextMatchingScore(List<Field> populatedFields, List<EsRuleItem> rulePremise) {
    if (populatedFields.size()==0 || rulePremise.size() ==0) {
      return 0;
    }
    else {
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
  }

  private boolean ruleItemMatchesPopulatedField(EsRuleItem ruleItem, Field populatedField) {
    String populatedFieldNormalizedPath = CedarTextUtils.normalizePath(populatedField.getFieldPath());
    String populatedFieldNormalizedValue = CedarFieldUtils.normalizeFieldValue(populatedField);

    if (populatedFieldNormalizedPath.equals(ruleItem.getFieldNormalizedPath()) &&
        populatedFieldNormalizedValue.equals(ruleItem.getFieldNormalizedValue())) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * This method creates and executes an Elasticsearch query that:
   * 1) Finds all association rules that contain the populated fields as premises and the target field as consequence.
   * 2) Aggregates all target field values in those rules and ranks them by (1) Elasticsearch max score, (2) max
   * confidence and (3) max support.
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
//  private SearchResponse esQueryRulesWithAggregationOfResults(Optional<String> templateId, List<Field> populatedFields, Field targetField,
//                                      boolean strictMatch, boolean filterByConfidence, boolean filterBySupport,
//                                      boolean useMappings) {
//
//    /** Query definition **/
//
//    BoolQueryBuilder mainBoolQuery = QueryBuilders.boolQuery();
//
//    if (filterByConfidence) {
//      RangeQueryBuilder minConfidence =
//          QueryBuilders.rangeQuery(INDEX_RULE_CONFIDENCE).from(MIN_CONFIDENCE_QUERY).includeLower(true);
//      mainBoolQuery = mainBoolQuery.must(minConfidence);
//    }
//
//    if (filterBySupport) {
//      RangeQueryBuilder minSupport =
//          QueryBuilders.rangeQuery(INDEX_RULE_SUPPORT).from(MIN_SUPPORT_QUERY).includeLower(true);
//      mainBoolQuery = mainBoolQuery.must(minSupport);
//    }
//
//    // If templateId is present, the query will be limited to rules from a particular template
//    if (templateId.isPresent()) {
//      MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery(INDEX_TEMPLATE_ID, templateId.get());
//      mainBoolQuery = mainBoolQuery.must(matchConsequenceField);
//    }
//
//    // Match number of premises
//    MatchQueryBuilder matchPremiseSize = QueryBuilders.matchQuery(INDEX_PREMISE_SIZE, populatedFields.size());
//    if (strictMatch) {
//      mainBoolQuery = mainBoolQuery.must(matchPremiseSize);
//    } else {
//      mainBoolQuery = mainBoolQuery.should(matchPremiseSize);
//    }
//
//    // Match number of consequences (i.e., 1)
//    MatchQueryBuilder matchConsequenceSize = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_SIZE, CONSEQUENCE_SIZE);
//    mainBoolQuery = mainBoolQuery.must(matchConsequenceSize);
//
//    // Match fields and values in premises
//    for (Field field : populatedFields) {
//
//      // Match field path
//      MatchQueryBuilder matchPremiseField = QueryBuilders.matchQuery(INDEX_PREMISE_NORMALIZED_PATH,
//          CedarTextUtils.normalizePath(field.getFieldPath()));
//
//      // Match field with other uris
//      MatchQueryBuilder matchPremiseFieldOtherUris = QueryBuilders.matchQuery(INDEX_PREMISE_TYPE_MAPPINGS,
//          field.getFieldPath());
//
//      // Match field bool query
//      BoolQueryBuilder premiseFieldBoolQuery = QueryBuilders.boolQuery();
//      premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseField);
//      if (useMappings) {
//        premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseFieldOtherUris);
//      }
//      premiseFieldBoolQuery = premiseFieldBoolQuery.minimumShouldMatch(1); // Logical OR
//
//      // Normalize value
//      String fieldValue = field.getFieldValue();
//      if (!CedarUtils.isUri(fieldValue)) { // Ontology term URIs will not be normalized
//        fieldValue = CedarTextUtils.normalizeValue(fieldValue);
//      }
//
//      // Match field normalized value
//      MatchQueryBuilder matchPremiseFieldNormalizedValue =
//          QueryBuilders.matchQuery(INDEX_PREMISE_NORMALIZED_VALUE, fieldValue);
//
//      // Match field normalized value with other uris
//      MatchQueryBuilder matchPremiseFieldNormalizedValues =
//          QueryBuilders.matchQuery(INDEX_PREMISE_VALUE_MAPPINGS, fieldValue);
//
//      // Match field value bool query
//      BoolQueryBuilder premiseFieldValueBoolQuery = QueryBuilders.boolQuery();
//      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValue);
//      if (useMappings) {
//        premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValues);
//      }
//      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.minimumShouldMatch(1); // Logical OR
//
//      // Premise bool query
//      BoolQueryBuilder premiseBoolQuery = QueryBuilders.boolQuery();
//      premiseBoolQuery = premiseBoolQuery.must(premiseFieldBoolQuery);
//      premiseBoolQuery = premiseBoolQuery.must(premiseFieldValueBoolQuery);
//
//      NestedQueryBuilder premiseNestedQuery = QueryBuilders.nestedQuery(INDEX_RULE_PREMISE, premiseBoolQuery,
//          ScoreMode.Avg);
//
//      // Add the premise query to the main query
//      if (strictMatch) {
//        mainBoolQuery = mainBoolQuery.must(premiseNestedQuery);
//      } else {
//        mainBoolQuery = mainBoolQuery.should(premiseNestedQuery);
//      }
//    }
//
//    // Match target field
//    MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_NORMALIZED_PATH,
//        CedarTextUtils.normalizePath(targetField.getFieldPath()));
//    MatchQueryBuilder matchConsequenceFieldOtherUris = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_TYPE_MAPPINGS,
//        targetField.getFieldPath());
//
//    // Match target field bool query
//    BoolQueryBuilder consequenceFieldBoolQuery = QueryBuilders.boolQuery();
//    consequenceFieldBoolQuery = consequenceFieldBoolQuery.should(matchConsequenceField);
//    if (useMappings) {
//      consequenceFieldBoolQuery = consequenceFieldBoolQuery.should(matchConsequenceFieldOtherUris);
//    }
//    consequenceFieldBoolQuery = consequenceFieldBoolQuery.minimumShouldMatch(1); // Logical OR
//
//    NestedQueryBuilder consequenceNestedQuery = QueryBuilders.nestedQuery(INDEX_RULE_CONSEQUENCE,
//        consequenceFieldBoolQuery, ScoreMode.Avg);
//
//    // Add the consequence query to the main query
//    mainBoolQuery = mainBoolQuery.must(consequenceNestedQuery);
//
//    /** Aggregations definition **/
//    List<BucketOrder> aggOrders = new ArrayList();
//    aggOrders.add(BucketOrder.aggregation(METRIC_MAX_SCORE_PATH, false));
//    aggOrders.add(BucketOrder.aggregation(METRIC_MAX_CONFIDENCE_PATH, false));
//    aggOrders.add(BucketOrder.aggregation(METRIC_SUPPORT_PATH, false));
//
//    // The following aggregations are used to group the values of the target fields by number of occurrences and sort
//    // them by confidence.
//    NestedAggregationBuilder mainAgg = AggregationBuilders
//        .nested(AGG_BY_NESTED_OBJECT, INDEX_RULE_CONSEQUENCE).subAggregation(
//            AggregationBuilders.terms(AGG_BY_TARGET_FIELD_VALUE_RESULT).field(INDEX_CONSEQUENCE_VALUE_RESULT).size(MAX_RESULTS).
//                order(aggOrders).subAggregation(
//                AggregationBuilders.reverseNested(AGG_METRICS_INFO).subAggregation(
//                    AggregationBuilders.max(METRIC_MAX_SCORE).script(new Script("_score"))).subAggregation(
//                    AggregationBuilders.max(METRIC_SUPPORT).field(INDEX_RULE_SUPPORT)).subAggregation(
//                    AggregationBuilders.max(METRIC_MAX_CONFIDENCE).field(INDEX_RULE_CONFIDENCE))));
//
//    /**  Execute query and return search results **/
//    Client client = esQueryService.getClient();
//    String indexName = ConfigManager.getCedarConfig().getElasticsearchConfig().getIndexes().getRulesIndex().getName();
//    SearchRequestBuilder search = client.prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue())
//        .setQuery(mainBoolQuery).addAggregation(mainAgg);
//    logger.info("Search query in Query DSL:\n" + search);
//    SearchResponse response = search.execute().actionGet();
//
//    return response;
//  }

  //  private List<RecommendedValue> readValuesFromBuckets(SearchResponse response) {
//    List<RecommendedValue> recommendedValues = new ArrayList<>();
//
//    Nested aggNestedObject = response.getAggregations().get(AGG_BY_NESTED_OBJECT);
//    Terms aggTargetFieldValueResult = aggNestedObject.getAggregations().get(AGG_BY_TARGET_FIELD_VALUE_RESULT);
//    List<? extends Terms.Bucket> buckets = aggTargetFieldValueResult.getBuckets();
//    for (Terms.Bucket bucket : buckets) {
//      String value = bucket.getKeyAsString();
//      ReverseNested aggReverseNested = bucket.getAggregations().get(AGG_METRICS_INFO);
//      // Final score calculation
//      Max maxScore = aggReverseNested.getAggregations().get(METRIC_MAX_SCORE);
//      Max maxSupport = aggReverseNested.getAggregations().get(METRIC_SUPPORT);
//      Max maxConfidence = aggReverseNested.getAggregations().get(METRIC_MAX_CONFIDENCE);
//      Double score = maxScore.getValue() * maxConfidence.getValue() * maxSupport.getValue();
//      recommendedValues.add(new RecommendedValue(value, null, score,
//          maxConfidence.getValue(), maxSupport.getValue(), null));
//    }
//    return recommendedValues;
//  }


}

