package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.*;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarTextUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.server.search.elasticsearch.service.RulesIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
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
  public List<EsRule> generateRules(List<String> templateIds) {
    AssociationRulesService service = new AssociationRulesService();

    List<EsRule> rules = null;
    try {
      // Generate rules for all the templates (with instances) in the system
      if (templateIds.isEmpty()) {
        logger.info("Generating rules for all templates in the system");
        esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
        templateIds = esQueryService.getTemplateIds();
      }
      else {
        logger.info("Generating rules for the following templates: " + templateIds.toString());
      }
      for (String templateId : templateIds) {
        logger.info("Processing templateId: " + templateId);
        logger.info("Removing all rules for templateId: " + templateId + " from the index");
        long removedCount = rulesIndexingService.removeRulesFromIndex(templateId);
        logger.info(removedCount + " rules removed");
        logger.info("Generating rules for templateId: " + templateId);
        long startTime = System.currentTimeMillis();
        List<EsRule> allRules = service.generateRulesForTemplate(templateId);

        logger.info("Filtering rules by number of consequences");
        rules = AssociationRulesUtils.filterRulesByNumberOfConsequences(allRules, 1);
        logger.info("No. rules after filtering: " + rules.size());

        // Store the rules in Elasticsearch
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode rules = mapper.valueToTree(esRules);
//        vrIndexingService.indexTemplateRules(rules, templateId);

        // Index all the template rules in bulk
        logger.info("Indexing rules in Elasticsearch");
        esQueryService.indexRulesBulk(rules);
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
    return rules;
  }

  @Override
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField,
                                          boolean strictMatch) {
    // Find the rules that match the condition
    SearchResponse response = esQueryRules(Optional.ofNullable(templateId), populatedFields, targetField, strictMatch,
        FILTER_BY_CONFIDENCE, FILTER_BY_SUPPORT, USE_MAPPINGS);

    // Extract the recommendedValues from the search results
    List<RecommendedValue> recommendedValues = readValuesFromBuckets(response);

    return new Recommendation(targetField.getFieldPath(), recommendedValues);
  }

  /**
   * This method creates and executes an Elasticsearch query that:
   * 1) Finds all association rules that contain the populated fields as premises and the target field as consequence.
   * 2) Aggregates all target field values in those rules and ranks them by (1) Elasticsearch max score, (2) max
   *    confidence and (3) max support.
   *
   * @param templateId      Template identifier (optional). If it is provided, the query is limited to the rules of
   *                        a particular template. Otherwise, all the rules in the system are queried.
   * @param populatedFields Populated fields and their values.
   * @param targetField     Target field.
   * @param strictMatch     It performs a strict search in the sense that it will only return the rules that contain
   *                        premises that exactly match the populated fields and just one consequence, which corresponds
   *                        to the target field. If set to false, the search will be more flexible (using an
   *                        Elasticsearch SHOULD clause for the premises), so that it will match any rules whose
   *                        consequence matches the target field.
   * @param filterByConfidence Sets a minimum confidence threshold
   * @param filterBySupport Sets a minimum support threshold
   * @param useMappings For ontology uris, tries to match the uri with other equivalent uris
   * @return An Elasticsearch response.
   */
  private SearchResponse esQueryRules(Optional<String> templateId, List<Field> populatedFields, Field targetField,
                                      boolean strictMatch, boolean filterByConfidence, boolean filterBySupport,
                                      boolean useMappings) {

    /** Query definition **/

    BoolQueryBuilder mainBoolQuery = QueryBuilders.boolQuery();

    if (filterByConfidence) {
      RangeQueryBuilder minConfidence = QueryBuilders.rangeQuery(INDEX_RULE_CONFIDENCE).from(MIN_CONFIDENCE_QUERY).includeLower(true);
      mainBoolQuery = mainBoolQuery.must(minConfidence);
    }

    if (filterBySupport) {
      RangeQueryBuilder minSupport = QueryBuilders.rangeQuery(INDEX_RULE_SUPPORT).from(MIN_SUPPORT_QUERY).includeLower(true);
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
    }
    else {
      mainBoolQuery = mainBoolQuery.should(matchPremiseSize);
    }

    // Match number of consequences (i.e., 1)
    MatchQueryBuilder matchConsequenceSize = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_SIZE, CONSEQUENCE_SIZE);
    mainBoolQuery = mainBoolQuery.must(matchConsequenceSize);

    // Match fields and values in premises
    for (Field field : populatedFields) {

      // Match field path
      MatchQueryBuilder matchPremiseField = QueryBuilders.matchQuery(INDEX_PREMISE_NORMALIZED_PATH,
          CedarTextUtils.normalizePath(field.getFieldPath()));

      // Match field with other uris
      MatchQueryBuilder matchPremiseFieldOtherUris = QueryBuilders.matchQuery(INDEX_PREMISE_TYPE_MAPPINGS,
          field.getFieldPath());

      // Match field bool query
      BoolQueryBuilder premiseFieldBoolQuery = QueryBuilders.boolQuery();
      premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseField);
      if (useMappings) {
        premiseFieldBoolQuery = premiseFieldBoolQuery.should(matchPremiseFieldOtherUris);
      }
      premiseFieldBoolQuery = premiseFieldBoolQuery.minimumShouldMatch(1); // Logical OR

      // Normalize value
      String fieldValue = field.getFieldValue();
      if (!CedarUtils.isUri(fieldValue)) { // Ontology term URIs will not be normalized
        fieldValue = CedarTextUtils.normalizeValue(fieldValue);
      }

      // Match field normalized value
      MatchQueryBuilder matchPremiseFieldNormalizedValue =
          QueryBuilders.matchQuery(INDEX_PREMISE_NORMALIZED_VALUE, fieldValue);

      // Match field normalized value with other uris
      MatchQueryBuilder matchPremiseFieldNormalizedValues =
          QueryBuilders.matchQuery(INDEX_PREMISE_VALUE_MAPPINGS, fieldValue);

      // Match field value bool query
      BoolQueryBuilder premiseFieldValueBoolQuery = QueryBuilders.boolQuery();
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValue);
      if (useMappings) {
        premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValues);
      }
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.minimumShouldMatch(1); // Logical OR

      // Premise bool query
      BoolQueryBuilder premiseBoolQuery = QueryBuilders.boolQuery();
      premiseBoolQuery = premiseBoolQuery.must(premiseFieldBoolQuery);
      premiseBoolQuery = premiseBoolQuery.must(premiseFieldValueBoolQuery);

      NestedQueryBuilder premiseNestedQuery = QueryBuilders.nestedQuery(INDEX_RULE_PREMISE, premiseBoolQuery, ScoreMode.Avg);

      // Add the premise query to the main query
      if (strictMatch) {
        mainBoolQuery = mainBoolQuery.must(premiseNestedQuery);
      }
      else {
        mainBoolQuery = mainBoolQuery.should(premiseNestedQuery);
      }
    }

    // Match target field
    MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_NORMALIZED_PATH,
        CedarTextUtils.normalizePath(targetField.getFieldPath()));
    MatchQueryBuilder matchConsequenceFieldOtherUris = QueryBuilders.matchQuery(INDEX_CONSEQUENCE_TYPE_MAPPINGS,
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

    /** Aggregations definition **/
    List<BucketOrder> aggOrders = new ArrayList();
    aggOrders.add(BucketOrder.aggregation(METRIC_MAX_SCORE_PATH, false));
    aggOrders.add(BucketOrder.aggregation(METRIC_MAX_CONFIDENCE_PATH, false));
    aggOrders.add(BucketOrder.aggregation(METRIC_SUPPORT_PATH, false));

    // The following aggregations are used to group the values of the target fields by number of occurrences and sort
    // them by confidence.
    NestedAggregationBuilder mainAgg = AggregationBuilders
        .nested(AGG_BY_NESTED_OBJECT, INDEX_RULE_CONSEQUENCE).subAggregation(
            AggregationBuilders.terms(AGG_BY_TARGET_FIELD_VALUE_RESULT).field(INDEX_CONSEQUENCE_VALUE_RESULT).size(MAX_RESULTS).
                order(aggOrders).subAggregation(
                AggregationBuilders.reverseNested(AGG_METRICS_INFO).subAggregation(
                    AggregationBuilders.max(METRIC_MAX_SCORE).script(new Script("_score"))).subAggregation(
                    AggregationBuilders.max(METRIC_SUPPORT).field(INDEX_RULE_SUPPORT)).subAggregation(
                    AggregationBuilders.max(METRIC_MAX_CONFIDENCE).field(INDEX_RULE_CONFIDENCE))));

    /**  Execute query and return search results **/
    Client client = esQueryService.getClient();
    String indexName = ConfigManager.getCedarConfig().getElasticsearchConfig().getIndexes().getRulesIndex().getName();
    SearchRequestBuilder search = client.prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue())
        .setQuery(mainBoolQuery).addAggregation(mainAgg);
    logger.info("Search query in Query DSL:\n" + search);
    SearchResponse response = search.execute().actionGet();

    return response;
  }

  private List<RecommendedValue> readValuesFromBuckets(SearchResponse response) {
    List<RecommendedValue> recommendedValues = new ArrayList<>();

    Nested aggNestedObject = response.getAggregations().get(AGG_BY_NESTED_OBJECT);
    Terms aggTargetFieldValueResult = aggNestedObject.getAggregations().get(AGG_BY_TARGET_FIELD_VALUE_RESULT);
    List<? extends Terms.Bucket> buckets = aggTargetFieldValueResult.getBuckets();
    for (Terms.Bucket bucket : buckets) {
      String value = bucket.getKeyAsString();
      ReverseNested aggReverseNested = bucket.getAggregations().get(AGG_METRICS_INFO);
      // Final score calculation
      Max maxScore = aggReverseNested.getAggregations().get(METRIC_MAX_SCORE);
      Max maxSupport = aggReverseNested.getAggregations().get(METRIC_SUPPORT);
      Max maxConfidence = aggReverseNested.getAggregations().get(METRIC_MAX_CONFIDENCE);
      Double score = maxScore.getValue() * maxConfidence.getValue() * maxSupport.getValue();
      recommendedValues.add(new RecommendedValue(value, null, score,
          maxConfidence.getValue(), maxSupport.getValue(), null));
    }
    return recommendedValues;
  }

}

