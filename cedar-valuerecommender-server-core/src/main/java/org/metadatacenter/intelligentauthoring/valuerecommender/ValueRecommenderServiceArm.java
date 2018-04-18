package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.*;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilders;
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
import org.metadatacenter.intelligentauthoring.valuerecommender.mappings.MappingsService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarTextUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.server.search.elasticsearch.service.ValueRecommenderIndexingService;
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
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField, boolean strictMatch) throws
      IOException {

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
      RangeQueryBuilder minConfidence = QueryBuilders.rangeQuery("confidence").from(MIN_CONFIDENCE_QUERY).includeLower(true);
      mainBoolQuery = mainBoolQuery.must(minConfidence);
    }

    if (filterBySupport) {
      RangeQueryBuilder minSupport = QueryBuilders.rangeQuery("support").from(MIN_SUPPORT_QUERY).includeLower(true);
      mainBoolQuery = mainBoolQuery.must(minSupport);
    }

    // If templateId is present, the query will be limited to rules from a particular template
    if (templateId.isPresent()) {
      MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery("templateId", templateId.get());
      mainBoolQuery = mainBoolQuery.must(matchConsequenceField);
    }

    // Match number of premises
    MatchQueryBuilder matchPremiseSize = QueryBuilders.matchQuery("premiseSize", populatedFields.size());
    if (strictMatch) {
      mainBoolQuery = mainBoolQuery.must(matchPremiseSize);
    }
    else {
      mainBoolQuery = mainBoolQuery.should(matchPremiseSize);
    }

    // Match number of consequences (i.e., 1)
    MatchQueryBuilder matchConsequenceSize = QueryBuilders.matchQuery("consequenceSize", 1);
    mainBoolQuery = mainBoolQuery.must(matchConsequenceSize);

    // Match fields and values in premises
    for (Field field : populatedFields) {

      // Match field (by id) (Note that we compare the path to the normalized path)
      MatchQueryBuilder matchPremiseField = QueryBuilders.matchQuery("premise.fieldNormalizedPath", field.getFieldPath());

      String fieldValue = field.getFieldValue();
      if (!CedarUtils.isUri(fieldValue)) { // Ontology term uris will not be normalized
        fieldValue = CedarTextUtils.normalizeValue(fieldValue);
      }

      // Match field normalized value
      MatchQueryBuilder matchPremiseFieldNormalizedValue =
          QueryBuilders.matchQuery("premise.fieldNormalizedValue", fieldValue);

      // Match field normalized value with other uris
      MatchQueryBuilder matchPremiseFieldNormalizedValues =
          QueryBuilders.matchQuery("premise.fieldNormalizedValues", fieldValue);


      // Match field value bool query
      BoolQueryBuilder premiseFieldValueBoolQuery = QueryBuilders.boolQuery();
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValue);
      if (useMappings) {
        premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.should(matchPremiseFieldNormalizedValues);
      }
      premiseFieldValueBoolQuery = premiseFieldValueBoolQuery.minimumShouldMatch(1); // Logical OR

      // Premise bool query
      BoolQueryBuilder premiseBoolQuery = QueryBuilders.boolQuery();
      premiseBoolQuery = premiseBoolQuery.must(matchPremiseField);
      premiseBoolQuery = premiseBoolQuery.must(premiseFieldValueBoolQuery);

      NestedQueryBuilder premiseNestedQuery = QueryBuilders.nestedQuery("premise", premiseBoolQuery, ScoreMode.Avg);

      // Add the premise query to the main query
      if (strictMatch) {
        mainBoolQuery = mainBoolQuery.must(premiseNestedQuery);
      }
      else {
        mainBoolQuery = mainBoolQuery.should(premiseNestedQuery);
      }
    }

    // Match target field (Note that we compare the path to the normalized path)
    MatchQueryBuilder matchConsequenceField = QueryBuilders.matchQuery("consequence.fieldNormalizedPath", targetField
        .getFieldPath());
    NestedQueryBuilder consequenceNestedQuery = QueryBuilders.nestedQuery("consequence", matchConsequenceField,
        ScoreMode.Avg);

    // Add the consequence query to the main query
    mainBoolQuery = mainBoolQuery.must(consequenceNestedQuery);

    /** Aggregations definition **/
    List<Terms.Order> aggOrders = new ArrayList();
    aggOrders.add(Terms.Order.aggregation("metrics_info>max_score", false));
    aggOrders.add(Terms.Order.aggregation("metrics_info>max_confidence", false));
    aggOrders.add(Terms.Order.aggregation("metrics_info>max_support", false));


    // The following aggregations are used to group the values of the target fields by number of occurrences and sort
    // them by confidence.
    NestedAggregationBuilder mainAgg = AggregationBuilders
        .nested("by_nested_object", "consequence").subAggregation(
            AggregationBuilders.terms("by_target_field_value").field("consequence.fieldValue").size(MAX_RESULTS).
                order(aggOrders).subAggregation(
                AggregationBuilders.reverseNested("metrics_info").subAggregation(
                    AggregationBuilders.max("max_score").script(new Script("_score"))).subAggregation(
                    AggregationBuilders.max("max_support").field("support")).subAggregation(
                    AggregationBuilders.max("max_confidence").field("confidence"))));

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
      // Final score calculation
      Max maxScore = aggReverseNested.getAggregations().get("max_score");
      Max maxSupport = aggReverseNested.getAggregations().get("max_support");
      Max maxConfidence = aggReverseNested.getAggregations().get("max_confidence");
      Double score = maxScore.getValue() * maxConfidence.getValue() * maxSupport.getValue();
      recommendedValues.add(new RecommendedValue(value, null, score,
          maxConfidence.getValue(), maxSupport.getValue(), null));
    }
    return recommendedValues;
  }


}





















