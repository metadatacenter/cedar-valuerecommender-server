package org.metadatacenter.intelligentauthoring.valuerecommender.util.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants;
import org.metadatacenter.util.json.JsonMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.constant.ElasticsearchConstants.ES_DOCUMENT_CEDAR_ID;
import static org.metadatacenter.constant.ElasticsearchConstants.ES_TEMPLATEID_FIELD;

public class ElasticsearchQueryService {

  private ElasticsearchConfig elasticsearchConfig;
  private Client client = null;
  private TimeValue scrollTimeout;
  private int scrollLimit = 5000;

  public ElasticsearchQueryService(ElasticsearchConfig esc) throws UnknownHostException {
    this.elasticsearchConfig = esc;
    this.scrollTimeout = TimeValue.timeValueMinutes(2);

    Settings settings = Settings.settingsBuilder()
        .put("cluster.name", esc.getClusterName()).build();

    client = TransportClient.builder().settings(settings).build().addTransportAddress(new
        InetSocketTransportAddress(InetAddress.getByName(esc.getHost()), esc.getTransportPort()));
  }

  // It uses the scroll API to retrieve all results
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/5.6/search-request-scroll.html
  // https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-search-scrolling.html
  public List<String> getTemplateInstancesIdsByTemplateId(String templateId) {
    List<String> templateInstancesIds = new ArrayList<>();

    QueryBuilder templateIdQuery = QueryBuilders.termQuery(ES_TEMPLATEID_FIELD, templateId);

    SearchResponse scrollResp = client.prepareSearch(elasticsearchConfig.getIndexName())
        .setQuery(templateIdQuery).setScroll(scrollTimeout).setSize(scrollLimit).get();

    while (scrollResp.getHits().hits().length != 0) { // Zero hits mark the end of the scroll and the while loop
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        templateInstancesIds.add(hit.sourceAsMap().get(ES_DOCUMENT_CEDAR_ID).toString());
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(scrollTimeout).execute().actionGet();
    }
    return templateInstancesIds;
  }

  public JsonNode getTemplateSummary(String templateId) {

    QueryBuilder templateIdQuery = QueryBuilders.termQuery(ES_DOCUMENT_CEDAR_ID, templateId);

    SearchResponse response = client.prepareSearch(elasticsearchConfig.getIndexName()).setTypes("content").setQuery(templateIdQuery).get();

    if (response.getHits().hits().length == 0) {
      throw new InternalError("Summarized content not found for template (templateId=" + templateId + ")");
    }
    else {
      String summarizedContent = response.getHits().hits()[0].sourceAsMap().get(Constants.SUMMARIZED_CONTENT_FIELD).toString();
      return JsonMapper.MAPPER.valueToTree(summarizedContent);
    }
  }

}