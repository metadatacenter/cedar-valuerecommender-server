package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.InternalNested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ValueRecommenderService implements IValueRecommenderService {

  private String esCluster;
  private String esHost;
  private String esIndex;
  private String esType;
  private int esTransportPort;
  private int size;
  private Settings settings;
  private Client client = null;

  private final String resourceContentFieldName = "resourceSummarizedContent";
  private final String fieldValueType = "fieldValueType";


  public ValueRecommenderService(String esCluster, String esHost, String esIndex, String esType, int esTransportPort,
                                 int size) {
    this.esCluster = esCluster;
    this.esHost = esHost;
    this.esIndex = esIndex;
    this.esType = esType;
    this.esTransportPort = esTransportPort;
    this.size = size;

    settings = Settings.settingsBuilder()
        .put("cluster.name", esCluster).build();
  }

  public boolean hasInstances(String templateId) throws UnknownHostException {
    templateId = templateId.toLowerCase();
    SearchResponse response = null;
    try {
      client = getClient();
      QueryBuilder qb = QueryBuilders.termQuery("templateId", templateId);
      SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
          .setQuery(qb);
      //System.out.println("Search query in Query DSL: " +  search.internalBuilder());
      response = search.execute().actionGet();
    } catch (UnknownHostException e) {
      throw e;
    }

    int hits = response.getHits().getHits().length;

    if (hits > 0) {
      return true;
    } else {
      return false;
    }
  }

  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      IOException {
    List<RecommendedValue> recommendedValues = null;
    templateId = templateId.toLowerCase();

    if (populatedFields.size() == 0) {
      recommendedValues = getContextIndependentRecommendation(templateId, populatedFields, targetField);
    } else {
      recommendedValues = getContextDependentRecommendation(templateId, populatedFields, targetField);
      if (recommendedValues.size() == 0) {
        recommendedValues = getContextIndependentRecommendation(templateId, populatedFields, targetField);
      }
    }
    Recommendation recommendation = new Recommendation(targetField.getFieldPath(), recommendedValues);
    return recommendation;
  }

  private List<RecommendedValue> getContextIndependentRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      IOException {
    List<RecommendedValue> recommendedValues = new ArrayList<>();
    if (templateId != null) {
      // Main query
      TermQueryBuilder templateIdQuery = QueryBuilders.termQuery("templateId", templateId);

      /* Now, we define the aggregations that will be applied to the results of the main query */
      // Nested aggregation to query the appropriate object
      String nestedObjectPath = getNestedObjectPath(targetField.getFieldPath());
      NestedBuilder nestedAgg = AggregationBuilders.nested("by_nested_object").path(nestedObjectPath);
      // Terms aggregation by value
      String esFieldValuePath = getElasticSearchFieldValuePath(templateId, targetField.getFieldPath());
      TermsBuilder targetFieldValueAgg = AggregationBuilders.terms("by_target_field_value").field(esFieldValuePath);
      // Terms aggregation by value's semantic type
      String esFieldValueAndStPath = getElasticSearchFieldValueAndSemanticTypePath(targetField.getFieldPath());
      TermsBuilder targetFieldValueAndStAgg = AggregationBuilders.terms("by_target_field_value_and_st").field
          (esFieldValueAndStPath);
      // Build aggregation
      nestedAgg.subAggregation(targetFieldValueAgg).subAggregation(targetFieldValueAndStAgg);

      SearchResponse response = null;
      try {
        client = getClient();
        // Prepare search
        SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
            .setQuery(templateIdQuery).addAggregation(nestedAgg);
        //System.out.println("Search query in Query DSL: " + search.internalBuilder());
        // Execute search
        response = search.execute().actionGet();
        //System.out.println("Search response: " + response.toString());
      } catch (UnknownHostException e) {
        throw e;
      }

      // Retrieve the relevant information and generate output
      InternalNested n = response.getAggregations().get(nestedAgg.getName());
      Terms tValue = n.getAggregations().get(targetFieldValueAgg.getName());
      Terms tValueAndSt = n.getAggregations().get(targetFieldValueAndStAgg.getName());
      recommendedValues = getRecommendedValuesFromTermAggregations(tValue, tValueAndSt, RecommendedValue
          .RecommendationType.CONTEXT_INDEPENDENT, populatedFields.size());
    }
    // If templateId == null
    else {
      // Do nothing
    }
    return recommendedValues;
  }

  private List<RecommendedValue> getContextDependentRecommendation(String templateId, List<Field> populatedFields,
                                                                   Field targetField) throws IOException {
    List<RecommendedValue> recommendedValues = new ArrayList<>();
    if (templateId != null) {
      // Main query
      TermQueryBuilder mainQuery = QueryBuilders.termQuery("templateId", templateId);

      // Now, we define the aggregations that will be applied to the results of the main query.

      // Terms aggregation by value
      String esFieldValuePath = getElasticSearchFieldValuePath(templateId, targetField.getFieldPath());
      TermsBuilder targetFieldValueAgg = AggregationBuilders.terms("by_target_field_value").field(esFieldValuePath);
      // Terms aggregation by value's semantic type
      String esFieldValueAndStPath = getElasticSearchFieldValueAndSemanticTypePath(targetField.getFieldPath());
      TermsBuilder targetFieldValueAndStAgg = AggregationBuilders.terms("by_target_field_value_and_st").field
          (esFieldValueAndStPath);

      // We use the populated fields to filter the aggregation, and narrow down the current
      // aggregation context to a specific set of documents.
      Map<String, List<Field>> fieldsGroupedByNestedObjectPath = groupByNestedObjectPath(populatedFields);

      // Iterate through the map and define the corresponding filters
      NestedBuilder mainAgg = null;
      NestedBuilder tmpAgg = null;
      String nestedObjectPath = null;
      int countNestedObjAgg = 1;
      int countPopFieldsAgg = 1;
      Iterator it = fieldsGroupedByNestedObjectPath.entrySet().iterator();
      // We define the aggregations for the populated fields, from the most granular to the most general level
      int i = 0;
      while (it.hasNext()) {
        Map.Entry<String, List<Field>> entry = (Map.Entry<String, List<Field>>) it.next();
        // We define a nested aggregation to filter at the appropriate level
        nestedObjectPath = entry.getKey();
        NestedBuilder nestedAgg = AggregationBuilders.nested("by_nested_object_" + countNestedObjAgg++).path
            (nestedObjectPath);
        // Bool filter for all populated fields
        BoolQueryBuilder filters = QueryBuilders.boolQuery();
        List<Field> fields = entry.getValue();
        for (Field f : fields) {
          String esFieldValuePath2 = getElasticSearchFieldValuePath(templateId, f.getFieldPath());
          TermQueryBuilder fieldFilter = QueryBuilders.termQuery(esFieldValuePath2, f.getFieldValue());
          filters = filters.must(fieldFilter);
        }
        // Aggregation for the populated fields
        FilterAggregationBuilder populatedFieldsAgg =
            AggregationBuilders.filter("by_populated_fields_" + countPopFieldsAgg++).filter(filters);
        // Most granular level. We have to nest the aggregation for the target field
        if (i == 0) {
          tmpAgg = nestedAgg;
          // This is the most granular level. Nest aggregation for the target field
          // Check if it is necessary to define the aggregation for the nested object, or if it has already been defined
          String nestedObjectPathTargetField = getNestedObjectPath(targetField.getFieldPath());
          if (nestedObjectPathTargetField.equals(nestedObjectPath)) {
            tmpAgg.subAggregation(populatedFieldsAgg.subAggregation(targetFieldValueAgg).subAggregation
                (targetFieldValueAndStAgg));
          } else {
            NestedBuilder nestedAggTargetField =
                AggregationBuilders.nested("by_nested_object_target_field").path(nestedObjectPathTargetField);
            // Build aggregation
            nestedAggTargetField.subAggregation(targetFieldValueAgg).subAggregation(targetFieldValueAndStAgg);
            // Add this aggregation to the main one
            tmpAgg.subAggregation(populatedFieldsAgg.subAggregation(nestedAggTargetField));
          }
        } else {
          nestedAgg.subAggregation(populatedFieldsAgg.subAggregation(tmpAgg));
          tmpAgg = nestedAgg;
        }
        i++;
      }
      mainAgg = tmpAgg;

      SearchResponse response = null;
      try {
        client = getClient();
        // Prepare search
        SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType).setQuery(mainQuery);
        // Add aggregation
        search.addAggregation(mainAgg);
        //System.out.println("Search query in Query DSL: " + search.internalBuilder());
        // Execute search
        response = search.execute().actionGet();
        //System.out.println("Search response: " + response.toString());
//      System.out.println("Number of results returned: " + response.getHits().getHits().length);
//      System.out.println("Total number of results: " + response.getHits().getTotalHits());
      } catch (UnknownHostException e) {
        throw e;
      }

      // Retrieve the relevant information and generate output
      //Collection<Terms.Bucket> buckets = getBucketsFromAggregations(response.getAggregations());
      List<Terms> termsAggs = getTermAggregations(response.getAggregations());
      Terms tValue = null;
      Terms tValueAndSt = null;
      if (termsAggs.get(0).getName().equals(targetFieldValueAgg.getName())) {
        tValue = termsAggs.get(0);
        tValueAndSt = termsAggs.get(1);
      } else if (termsAggs.get(0).getName().equals(targetFieldValueAndStAgg.getName())) {
        tValueAndSt = termsAggs.get(0);
        tValue = termsAggs.get(1);
      }
      recommendedValues = getRecommendedValuesFromTermAggregations(tValue, tValueAndSt,
          RecommendedValue.RecommendationType.CONTEXT_DEPENDENT, populatedFields.size());
    }
    // If templateId == null
    else {
      // Do nothing
    }
    return recommendedValues;
  }

  // Sample field: characteristic.name
  // Expected output: resourceSummarizedContent.characteristic.name_field.fieldValue_string
  private String getElasticSearchFieldValuePath(String templateId, String fieldPath) throws IOException {
    // ElasticSearch field path and name
    String esFieldPath = resourceContentFieldName + "." + fieldPath + "_field";
    String esFieldJsonPath = "/" + esFieldPath.replace('.', '/');
    //String esFieldName = fieldPath.substring(fieldPath.lastIndexOf('.')) + "_field";

    /* Get field data type from the indexed template */
    JsonNode indexedTemplateSource = getTemplateFromIndex(templateId);

    /* Access to field information */
    JsonNode field = indexedTemplateSource.at(esFieldJsonPath);

    if (field.size() > 0) {
      if (field.hasNonNull(fieldValueType)) {
        String esFieldValuePath = esFieldPath + ".fieldValue_" + field.get(fieldValueType).asText();
        return esFieldValuePath;
      } else {
        throw new InternalError("Field type not found");
      }
    } else {
      throw new IllegalArgumentException("Field not found: " + fieldPath);
    }
  }

  // Sample field: study.disease
  // Expected output: resourceSummarizedContent.study.disease_field.fieldValueAndSemanticType
  private String getElasticSearchFieldValueAndSemanticTypePath(String fieldPath) throws IOException {
    // ElasticSearch field path and name
    return resourceContentFieldName + "." + fieldPath + "_field" + ".fieldValueAndSemanticType";
  }

  // TODO: cache the template being used to avoid multiple calls
  private JsonNode getTemplateFromIndex(String templateId) throws IOException {
    // Main query
    TermQueryBuilder templateQuery = QueryBuilders.termQuery("info.@id", templateId);
    SearchResponse response = null;
    client = getClient();
    // Prepare search
    SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
        .setQuery(templateQuery);
    //System.out.println("Search query in Query DSL: " + search.internalBuilder());

    // Execute search
    response = search.execute().actionGet();
    //System.out.println("Search response: " + response.toString());
    if (response.getHits().totalHits() > 0) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(response.getHits().getAt(0).getSourceAsString());
      return source;
    } else {
      throw new IllegalArgumentException("Template Id not found: " + templateId);
    }
  }

  private String getNestedObjectPath(String fieldPath) {
    String nestedObjectPath = resourceContentFieldName;
    if (fieldPath.contains(".")) {
      String nestedPath = fieldPath.substring(0, fieldPath.lastIndexOf('.'));
      nestedObjectPath = nestedObjectPath + "." + nestedPath;
    }
    return nestedObjectPath;
  }

  private Map<String, List<Field>> groupByNestedObjectPath(List<Field> populatedFields) {
    // Paths are ordered from more granular to less granular, in order to facilitate the definition
    // of the ElasticSearch aggregations
    Map<String, List<Field>> groups = new TreeMap<String, List<Field>>(
        new Comparator<String>() {
          @Override
          public int compare(String s1, String s2) {
            int dotsS1 = s1.length() - s1.replace(".", "").length();
            int dotsS2 = s2.length() - s2.replace(".", "").length();
            return Integer.compare(dotsS2, dotsS1);
          }
        }
    );
    for (Field f : populatedFields) {
      String nestedObjectPath = getNestedObjectPath(f.getFieldPath());
      if (groups.containsKey(nestedObjectPath)) {
        List<Field> fields = groups.get(nestedObjectPath);
        fields.add(f);
        groups.put(nestedObjectPath, fields);
      } else {
        List<Field> fields = new ArrayList<>();
        fields.add(f);
        groups.put(nestedObjectPath, fields);
      }
    }

//    for (Map.Entry<String, List<Field>> entry : groups.entrySet()) {
//      System.out.println("Key: " + entry.getKey() + "; Value: " + entry.getValue());
//    }
    return groups;
  }

  private List<Terms> getTermAggregations(Aggregations aggs) {
    List<Terms> termsAgg = new ArrayList<>();
    for (Aggregation agg : aggs) {
      if (agg instanceof InternalNested) {
        return getTermAggregations(((InternalNested) agg).getAggregations());
      } else if (agg instanceof Filter) {
        return getTermAggregations(((Filter) agg).getAggregations());
      } else if (agg instanceof Terms) {
        termsAgg.add((Terms) agg);
      } else {
        throw new InternalError("Unexpected aggregation type");
      }
    }
    return termsAgg;
  }

  private List<RecommendedValue> getRecommendedValuesFromTermAggregations(Terms valueAgg, Terms valueAndStAgg,
                                                                          RecommendedValue.RecommendationType recommendationType,
                                                                          int numberPopulatedFields) {
    List<RecommendedValue> recommendedValues = new ArrayList<>();
    Collection<Terms.Bucket> buckets = null;
    if (valueAndStAgg.getBuckets().size() > 0) {
      buckets = valueAndStAgg.getBuckets();
    } else if (valueAgg.getBuckets().size() > 0) {
      buckets = valueAgg.getBuckets();
    }
    // No results
    else {
      return recommendedValues;
    }

    // Calculate total docs
    double totalDocs = 0;
    for (Terms.Bucket b : buckets) {
      if (b.getKeyAsString().trim().length() > 0) {
        totalDocs += b.getDocCount();
      }
    }

    // If there are results containing the semantic type, we use them. Otherwise, we use the regular values
    Iterator<Terms.Bucket> it = buckets.iterator();
    while (it.hasNext()) {
      Terms.Bucket b = it.next();
      String value = null;
      if (b.getKeyAsString().trim().length() > 0) {
        value = b.getKeyAsString().trim();
      }
      // The score will be calculated as the percentage of samples for the particular value, with respect to the
      // total number of samples for all values
      double score = b.getDocCount() / totalDocs;
      // Then, the score is weighted depending on the recommendation type and on the number of populated fields
      if (recommendationType.equals(RecommendedValue.RecommendationType.CONTEXT_INDEPENDENT) && (numberPopulatedFields > 0)) {
        score = score / (numberPopulatedFields + 1);
      }
      // Regular value
      String delimiter = "[[ST]]";
      if (value.contains(delimiter)) {
        String[] v = value.split("\\[\\[ST\\]\\]");
        recommendedValues.add(new RecommendedValue(v[0], v[1], score, recommendationType));
      } else {
        recommendedValues.add(new RecommendedValue(value, null, score, recommendationType));
      }
    }
    return recommendedValues;
  }

  /**
   * Index GEO data
   */
//  public void indexGEO() {
//    Client client = null;
//    try {
//      client = getClient();
//      String path = System.getenv("CEDAR_HOME") + "cedar-valuerecommender-server/data/sample-data/GEOFlat3Samples";
//      Util.indexAllFilesInFolder(client, "cedar", "template_instances", path);
//    } catch (IOException e) {
//      e.printStackTrace();
//    } finally {
//      // Close client
//      client.close();
//    }
//  }

  private Client getClient() throws UnknownHostException {
    if (client == null) {
      client = TransportClient.builder().settings(settings).build().addTransportAddress(new
          InetSocketTransportAddress(InetAddress.getByName(esHost), esTransportPort));
    }
    return client;
  }

  public void closeClient() {
    client.close();
  }

}
