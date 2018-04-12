package org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.model.search.IndexedDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.constant.ElasticsearchConstants.ES_DOCUMENT_CEDAR_ID;
import static org.metadatacenter.constant.ElasticsearchConstants.ES_TEMPLATEID_FIELD;

public class ElasticsearchQueryService {

  private ElasticsearchConfig elasticsearchConfig;
  private Client client = null;
  private TimeValue scrollTimeout;
  private int scrollLimit = 5000;

  protected final Logger logger = LoggerFactory.getLogger(ElasticsearchQueryService.class);

  public ElasticsearchQueryService(ElasticsearchConfig esc) throws UnknownHostException {
    this.elasticsearchConfig = esc;
    this.scrollTimeout = TimeValue.timeValueMinutes(2);

    Settings settings = Settings.builder()
        .put("cluster.name", esc.getClusterName()).build();

    client = new PreBuiltTransportClient(settings).addTransportAddress(new
        InetSocketTransportAddress(InetAddress.getByName(esc.getHost()), esc.getTransportPort()));
  }

  public Client getClient() {
    return client;
  }

  // It uses the scroll API to retrieve all results
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/5.6/search-request-scroll.html
  // https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-search-scrolling.html
  public List<String> getTemplateInstancesIdsByTemplateId(String templateId) {
    List<String> templateInstancesIds = new ArrayList<>();

    QueryBuilder templateIdQuery = QueryBuilders.termQuery(ES_TEMPLATEID_FIELD, templateId);

    SearchResponse scrollResp = client.prepareSearch(elasticsearchConfig.getIndexes().getSearchIndex().getName())
        .setQuery(templateIdQuery).setScroll(scrollTimeout).setSize(scrollLimit).get();

    while (scrollResp.getHits().getHits().length != 0) { // Zero hits mark the end of the scroll and the while loop
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        templateInstancesIds.add(hit.getSourceAsMap().get(ES_DOCUMENT_CEDAR_ID).toString());
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(scrollTimeout).execute().actionGet();
    }
    return templateInstancesIds;
  }

  public List<String> getTemplateIds() {
    List<String> templateIds = new ArrayList<>();

    QueryBuilder templateIdsQuery = QueryBuilders.termQuery("info.nodeType", "template");

    SearchResponse scrollResp = client.prepareSearch(elasticsearchConfig.getIndexes().getSearchIndex().getName())
        .setQuery(templateIdsQuery).setScroll(scrollTimeout).setSize(scrollLimit).get();

    while (scrollResp.getHits().getHits().length != 0) { // Zero hits mark the end of the scroll and the while loop
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        templateIds.add(hit.getSourceAsMap().get(ES_DOCUMENT_CEDAR_ID).toString());
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(scrollTimeout).execute().actionGet();
    }
    return templateIds;
  }

  /**
   * Index all rules in a single API call
   *
   * @param rules
   */
  public void indexRulesBulk(List<EsRule> rules) {

    if (rules.size() > 0) {

      BulkRequestBuilder bulkRequest = client.prepareBulk();
      ObjectMapper mapper = new ObjectMapper();

      int count = 0;
      for (EsRule rule : rules) {
        // either use client#prepare, or use Requests# to directly build index/delete requests
        bulkRequest.add(client.prepareIndex(elasticsearchConfig.getIndexes().getValueRecommenderIndex().getName(),
            elasticsearchConfig.getIndexes().getValueRecommenderIndex().getType(IndexedDocumentType.RULES_DOC), Integer
                .toString
                    (count++)).setSource(mapper.convertValue(rule, Map.class))
        );
      }

      BulkResponse bulkResponse = bulkRequest.get();
      if (bulkResponse.hasFailures()) {
        // process failures by iterating through each bulk response item
        logger.error("Failure when processing bulk request:");
        logger.error(bulkResponse.buildFailureMessage());
      }
    }
    else {
      logger.warn("There are no rules to be indexed");
    }
  }

//  public JsonNode getTemplateSummary(String templateId) {
//
//    QueryBuilder templateIdQuery = QueryBuilders.termQuery(ES_DOCUMENT_CEDAR_ID, templateId);
//
//    SearchResponse response = client.prepareSearch(elasticsearchConfig.getIndexes().getSearchIndex().getName())
// .setTypes("content").setQuery(templateIdQuery).get();
//
//    if (response.getHits().hits().length == 0) {
//      throw new InternalError("Summarized content not found for template (templateId=" + templateId + ")");
//    }
//    else {
//      Object summarizedContent = response.getHits().hits()[0].sourceAsMap().get(Constants.SUMMARIZED_CONTENT_FIELD);
//      JsonNode templateSummary = JsonMapper.MAPPER.convertValue(summarizedContent, JsonNode.class);
//      return templateSummary;
//    }
//  }

}