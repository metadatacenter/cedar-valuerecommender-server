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
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilter;
import org.elasticsearch.search.aggregations.bucket.nested.InternalNested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;

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
    templateId = templateId.toLowerCase();
    Client client = null;
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

    List<RecommendedValue> recommendedValues = new ArrayList<>();
    if (templateId != null) {
      templateId = templateId.toLowerCase();

      // Main query
      BoolQueryBuilder mainQuery = QueryBuilders.boolQuery()
          .must(QueryBuilders.termQuery("templateId", templateId));

      // Bool query for populated fields
      BoolQueryBuilder populatedFieldsQuery = QueryBuilders.boolQuery();
      for (Field f : populatedFields) {
        mainQuery = mainQuery.must(QueryBuilders.nestedQuery("resourceFields",
            QueryBuilders.boolQuery().must(QueryBuilders.termQuery("resourceFields.fieldPath", f.getFieldPath().toLowerCase()))
                .must(QueryBuilders.termQuery("resourceFields.valueLabel", f.getFieldValue().toLowerCase()))));
      }
      
      // Now, we define the aggregations that will be applied to the results of the main query

      // Nested aggregation to query the resourceFields array
      NestedBuilder resourceFieldsAgg = AggregationBuilders.nested("resource_fields").path("resourceFields");

      // Filter aggregation by fieldPath
      FilterAggregationBuilder fieldPathAgg =
          AggregationBuilders.filter("by_field_path")
              .filter(QueryBuilders.termQuery("resourceFields.fieldPath", targetField.getFieldPath().toLowerCase()));

      // Terms aggregation by value
      TermsBuilder valueLabelAgg = AggregationBuilders.terms("by_value_label").field("resourceFields.valueLabel");

      // Build aggregation
      resourceFieldsAgg.subAggregation(fieldPathAgg.subAggregation(valueLabelAgg));

      Client client = null;
      SearchResponse response = null;
      try {
        client = getClient();

        // Prepare search
        SearchRequestBuilder search = client.prepareSearch(esIndex).setTypes(esType)
            .setQuery(mainQuery).addAggregation(resourceFieldsAgg);
        System.out.println("Search query in Query DSL: " + search.internalBuilder());

        // Execute search
        response = search.execute().actionGet();
        System.out.println("Search response: " + response.toString());
//      System.out.println("Number of results returned: " + response.getHits().getHits().length);
//      System.out.println("Total number of results: " + response.getHits().getTotalHits());
      } catch (UnknownHostException e) {
        throw e;
      } finally {
        // Close client
        client.close();
      }
      // Retrieve the relevant information and generate output
      InternalNested n = response.getAggregations().get(resourceFieldsAgg.getName());
      Filter f = n.getAggregations().get(fieldPathAgg.getName());
      Terms t = f.getAggregations().get(valueLabelAgg.getName());

      Collection<Terms.Bucket> buckets = t.getBuckets();

      // Calculate total docs
      double totalDocs = 0;
      for (Terms.Bucket b : buckets) {
        if (b.getKeyAsString().trim().length() > 0) {
          totalDocs += b.getDocCount();
        }
      }
      // The score will be calculated as the percentage of samples for the particular value, with respect to the
      // total number of samples for all values
      for (Terms.Bucket b : buckets) {
        if (b.getKeyAsString().trim().length() > 0) {
          double score = b.getDocCount() / totalDocs;
          recommendedValues.add(new RecommendedValue(b.getKeyAsString(), score));
        }
      }
    }
    // If templateId == null
    else {
      // Do nothing
    }
    System.out.println(recommendedValues);
    Recommendation recommendation = new Recommendation(targetField.getFieldPath(), recommendedValues);
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
