package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValueRecommenderService implements IValueRecommenderService {

  private String esCluster;
  private String esHost;
  private String esIndex;
  private String esType;
  private int esTransportPort;
  private int size;
  private Settings settings;

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

    Client client = null;
    SearchResponse response = null;
    try {
      client = getClient();
      QueryBuilder qb = QueryBuilders.matchQuery("templateId", templateId);
      SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
          .setQuery(qb);
      //System.out.println("Search query in Query DSL: " +  search.internalBuilder());
      response = search.execute().actionGet();
    } catch (UnknownHostException e) {
      throw e;
    } finally {
      // Close client
      client.close();
    }

    int hits = response.getHits().getHits().length;

    if (hits > 0) {
      return true;
    } else {
      return false;
    }
  }

  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      UnknownHostException {
    // TemplateId filter
    BoolQueryBuilder queryFilter = QueryBuilders.boolQuery();
    if (templateId != null) {
      queryFilter = queryFilter.must(QueryBuilders.termQuery("_templateId", templateId.toLowerCase()));
    }
    // Add filters for populated fields
    for (Field f : populatedFields) {
      queryFilter =
          queryFilter.must(QueryBuilders.termQuery(f.getFieldName(), f.getFieldValue()
              .toLowerCase()));
    }
    // Create the aggregation for the target field
    TermsBuilder aggTargetField = AggregationBuilders.terms("agg_target_field").size(size).field(targetField
        .getFieldName());
    // Create the filter aggregation using the previously defined aggregation and filter
    FilterAggregationBuilder aggRecommendation = AggregationBuilders.filter("agg_recommendation");
    aggRecommendation = aggRecommendation.filter(queryFilter).subAggregation(aggTargetField);

    Client client = null;
    SearchResponse response = null;
    try {
      client = getClient();

      SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
          .addAggregation(aggRecommendation);
      //System.out.println("Search query in Query DSL: " +  search.internalBuilder());

      // Execute the request
      response = search.execute().actionGet();
      //System.out.println("Search response: " + response.toString());
      //System.out.println("Number of results returned: " + response.getHits().getHits().length);
      //System.out.println("Total number of results: " + response.getHits().getTotalHits());
    } catch (UnknownHostException e) {
      throw e;
    } finally {
      // Close client
      client.close();
    }
    // Retrieve the relevant information and generate output
    Filter f = response.getAggregations().get(aggRecommendation.getName());
    Terms terms = f.getAggregations().get(aggTargetField.getName());
    Collection<Terms.Bucket> buckets = terms.getBuckets();
    List<RecommendedValue> recommendedValues = new ArrayList<>();

    // Calculate total number of samples
    double totalSamples = 0;
    for (Terms.Bucket b : buckets) {
      if (b.getKeyAsString().trim().length() > 0) {
        totalSamples += b.getDocCount();
      }
    }
    // The score will be calculated as the percentage of samples for the particular value, with respect to the
    // total number of samples for all values
    for (Terms.Bucket b : buckets) {
      if (b.getKeyAsString().trim().length() > 0) {
        double score = b.getDocCount() / totalSamples;
        recommendedValues.add(new RecommendedValue(b.getKeyAsString(), score));
      }
    }
    Recommendation recommendation = new Recommendation(targetField.getFieldName(), recommendedValues);
    return recommendation;
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

  /***
   * Private methods
   ***/

  private Client getClient() throws UnknownHostException {
    return TransportClient.builder().settings(settings).build().addTransportAddress(new
        InetSocketTransportAddress(InetAddress.getByName(esHost), esTransportPort));
  }

}
