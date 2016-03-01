package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValueRecommenderService {

  Node node;

  public ValueRecommenderService() {
    String esHome = "/usr/local/Cellar/elasticsearch21/2.1.2/libexec";
    // on startup
    node = NodeBuilder.nodeBuilder().settings(Settings.builder().put("path.home", esHome)).node();
  }

  public Recommendation getRecommendation(List<Field> populatedFields, Field targetField) {
    // Search
    // MatchAll on the whole cluster with all default options
    String index1 = "test";
    String type1 = "cars";

    String field = targetField.getFieldName();
    String agg1 = "agg1";
    Client client = node.client();

    SearchResponse response = client.prepareSearch(index1)
        .setTypes(type1)
        .addAggregation(AggregationBuilders.terms(agg1).field(field)).execute().actionGet();
    Terms terms = response.getAggregations().get(agg1);

    client.close();

    Collection<Terms.Bucket> buckets = terms.getBuckets();
    List<RecommendedValue> suggestedValues = new ArrayList<>();
    for (Terms.Bucket b : buckets) {
      RecommendedValue sv = new RecommendedValue(b.getKeyAsString(), b.getDocCount());
      suggestedValues.add(sv);
    }
    Recommendation r = new Recommendation(targetField.getFieldName(), suggestedValues);

    return r;
  }

//  public void shutdown() {
//    // on shutdown
//    node.close();
//  }

}
