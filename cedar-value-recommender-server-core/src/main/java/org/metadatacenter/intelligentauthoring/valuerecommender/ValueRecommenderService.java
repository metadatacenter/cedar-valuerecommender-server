package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValueRecommenderService {

  public Recommendation getRecommendation(List<Field> populatedFields, Field targetField) throws
      UnknownHostException {
    // Create the bool filter for populated fields
    BoolQueryBuilder populatedFieldsFilter = QueryBuilders.boolQuery();
    for (Field f : populatedFields) {
      populatedFieldsFilter =
          populatedFieldsFilter.must(QueryBuilders.termQuery(f.getFieldName().toLowerCase(), f.getFieldValue()
              .toLowerCase()));
    }

    // Create the aggregation for the target field
    TermsBuilder aggTargetField = AggregationBuilders.terms("agg_target_field").field(targetField.getFieldName()
        );

    // Create the filter aggregation using the previously defined aggregation and filter
    FilterAggregationBuilder aggRecommendation = AggregationBuilders.filter("agg_recommendation")
        .filter(populatedFieldsFilter).subAggregation(aggTargetField);

    Settings settings = Settings.settingsBuilder()
        .put("cluster.name", Constants.CLUSTER_NAME).build();
    Client client = TransportClient.builder().settings(settings).build().addTransportAddress(new
        InetSocketTransportAddress(InetAddress.getByName(Constants.ES_HOST), Constants.ES_TRANSPORT_PORT));

    /** This block can be used to index some data **/
//    try {
//      Util.indexAllFilesInFolder(client, "cedar", "template_instances", "data/sample-data/GEOFlatSamples");
//    } catch (IOException e) {
//      e.printStackTrace();
//    }

    SearchRequestBuilder search = client.prepareSearch(Constants.ES_INDEX_NAME).setTypes(Constants.ES_TYPE_NAME)
        .addAggregation(aggRecommendation);
    //System.out.println("Search query in Query DSL: " +  search.internalBuilder());

    // Execute the request
    SearchResponse response = search.execute().actionGet();
    //System.out.println("Search response: " + response.toString());

    // Retrieve the relevant information and generate output
    Filter f = response.getAggregations().get(aggRecommendation.getName());
    Terms terms = f.getAggregations().get(aggTargetField.getName());

    Collection<Terms.Bucket> buckets = terms.getBuckets();
    List<RecommendedValue> recommendedValues = new ArrayList<>();
    for (Terms.Bucket b : buckets) {
      if (b.getKeyAsString().trim().length() > 0) {
        recommendedValues.add(new RecommendedValue(b.getKeyAsString(), b.getDocCount()));
      }
    }
    Recommendation recommendation = new Recommendation(targetField.getFieldName(), recommendedValues);

    // Close client
    client.close();

    return recommendation;
  }

  /**
   * Index GEO data
   */
//  public void indexGEO() throws IOException {
//    Settings settings = Settings.settingsBuilder()
//        .put("cluster.name", Constants.CLUSTER_NAME).build();
//    Client client = TransportClient.builder().settings(settings).build().addTransportAddress(new
//        InetSocketTransportAddress(InetAddress.getByName(Constants.ES_HOST), Constants.ES_TRANSPORT_PORT));
//    Util.indexAllFilesInFolder(client, "cedar", "template_instances", "data/sample-data/GEOFlatSamples");
//  }

}
