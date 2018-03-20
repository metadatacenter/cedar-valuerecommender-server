package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.TextUtils;
import org.metadatacenter.server.search.elasticsearch.service.ValueRecommenderIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ValueRecommenderServiceArm implements IValueRecommenderArm {

  private final Logger logger = LoggerFactory.getLogger(ValueRecommenderServiceArm.class);
  private ValueRecommenderIndexingService vrIndexingService;
  private static ElasticsearchQueryService esQueryService;

  public ValueRecommenderServiceArm(CedarConfig config, ValueRecommenderIndexingService
      valueRecommenderIndexingService) {
    // Initialize configuration manager, which will provide access to the Cedar configuration
    ConfigManager.getInstance().initialize(config);
    vrIndexingService = valueRecommenderIndexingService;
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
        esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
        templateIds = esQueryService.getTemplateIds();
      }
      for (String templateId : templateIds) {
        logger.info("Removing all rules for templateId: " + templateId + " from the index");
        long removedCount = vrIndexingService.removeTemplateRulesFromIndex(templateId);
        logger.info("Removed " + removedCount + " rules from the index");

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
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      IOException {

    // Find the rules that match the condition
    SearchResponse response = esQueryRules(Optional.ofNullable(templateId), populatedFields, targetField);

    // Extract the recommendedValues from the search results
    List<RecommendedValue> recommendedValues = readValuesFromBuckets(response);

    return new Recommendation(targetField.getFieldPath(), recommendedValues);
  }

  /**
   * This method creates and executes an Elasticsearch query that:
   * 1) Finds all association rules that contain the populated fields as premises and the target field as consequence
   * . This search is strict in the sense that it will only return the rules that contain exactly the same number of
   * premises as populated fields and just one consequence, which corresponds to the target field.
   * 2) Aggregates all target field values in those rules and ranks them by rule confidence.
   *
   * @param templateId      Template identifier (optional). If it is provided, the query is limited to the rules of
   *                        a particular template. Otherwise, all the rules in the system are queried.
   * @param populatedFields Populated fields and their values.
   * @param targetField     Target field.
   * @return An Elasticsearch response.
   */
  private SearchResponse esQueryRules(Optional<String> templateId, List<Field> populatedFields, Field targetField) {

    /** Query definition **/

    BoolQueryBuilder mainBoolQuery = QueryBuilders.boolQuery();

    // If templateId is present, the query will be limited to rules from a particular template
    if (templateId.isPresent()) {
      MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery("templateId", templateId.get());
      mainBoolQuery = mainBoolQuery.must(matchConsequenceField);
    }

    // Match number of premises
    MatchQueryBuilder matchPremiseSize = QueryBuilders.matchQuery("premiseSize", populatedFields.size());
    mainBoolQuery = mainBoolQuery.must(matchPremiseSize);

    // Match number of consequences (i.e., 1)
    MatchQueryBuilder matchConsequenceSize = QueryBuilders.matchQuery("consequenceSize", 1);
    mainBoolQuery = mainBoolQuery.must(matchConsequenceSize);

    // Match fields and values in premises
    for (Field field : populatedFields) {

      // Match field (by id)
      MatchQueryBuilder matchPremiseField = QueryBuilders.matchQuery("premise.fieldPath", field.getFieldPath());

      // Match field normalized value
      MatchQueryBuilder matchPremiseFieldNormalizedValue =
          QueryBuilders.matchQuery("premise.fieldNormalizedValue", TextUtils.normalize(field.getFieldValue()));

      // Premise bool query
      BoolQueryBuilder premiseBoolQuery = QueryBuilders.boolQuery();
      premiseBoolQuery = premiseBoolQuery.must(matchPremiseField);
      premiseBoolQuery = premiseBoolQuery.must(matchPremiseFieldNormalizedValue);

      NestedQueryBuilder premiseNestedQuery = QueryBuilders.nestedQuery("premise", premiseBoolQuery, ScoreMode.Avg);

      // Add the premise query to the main query
      mainBoolQuery = mainBoolQuery.must(premiseNestedQuery);
    }

    // Match target field
    MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery("consequence.fieldPath", targetField
        .getFieldPath());
    NestedQueryBuilder consequenceNestedQuery = QueryBuilders.nestedQuery("consequence", matchConsequenceField,
        ScoreMode.Avg);

    // Add the consequence query to the main query
    mainBoolQuery = mainBoolQuery.must(consequenceNestedQuery);

    /** Aggregations definition **/
    // The following aggregations are used to group the values of the target fields by number of occurrences and sort
    // them by confidence.
    NestedAggregationBuilder mainAgg = AggregationBuilders
        .nested("by_nested_object", "consequence").subAggregation(
            AggregationBuilders.terms("by_target_field_value").field("consequence.fieldValue").size(1000).
                order(Terms.Order.aggregation("metrics_info>confidence_avg", false)).subAggregation(
                AggregationBuilders.reverseNested("metrics_info").subAggregation(
                    AggregationBuilders.avg("support_avg").field("support")).subAggregation(
                    AggregationBuilders.avg("confidence_avg").field("confidence"))));

    /**  Execute query and return search results **/

    Client client = esQueryService.getClient();
    String indexName = ConfigManager.getCedarConfig().getElasticsearchConfig().getIndexes().getValueRecommenderIndex
        ().getName();
    String type = "rulesDoc";
    SearchRequestBuilder search = client.prepareSearch(indexName).setTypes(type).setQuery(mainBoolQuery)
        .addAggregation(mainAgg);
    //logger.info("Search query in Query DSL:\n" + search);
    SearchResponse response = search.execute().actionGet();

    return response;
  }

  private List<RecommendedValue> readValuesFromBuckets(SearchResponse response) {
    List<RecommendedValue> recommendedValues = new ArrayList<>();

    Nested aggNestedObject = response.getAggregations().get("by_nested_object");
    Terms aggTargetFieldValue = aggNestedObject.getAggregations().get("by_target_field_value");
    List<? extends Terms.Bucket> buckets = aggTargetFieldValue.getBuckets();
    for (Terms.Bucket bucket : buckets) {
      String value = bucket.getKeyAsString();
      ReverseNested aggReverseNested = bucket.getAggregations().get("metrics_info");
      Avg aggAvg = aggReverseNested.getAggregations().get("support_avg");
      Double score = aggAvg.getValue();
      recommendedValues.add(new RecommendedValue(value, null, score, null));
    }
    return recommendedValues;
  }


}





















