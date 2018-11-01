package org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants;
import org.metadatacenter.search.IndexedDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.metadatacenter.constant.ElasticsearchConstants.*;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.INDEX_TEMPLATE_ID;

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
        TransportAddress(InetAddress.getByName(esc.getHost()), esc.getTransportPort()));
  }

  public Client getClient() {
    return client;
  }

  // It uses the scroll API to retrieve all results
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html
  // https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-search-scrolling.html
  public List<String> getTemplateInstancesIdsByTemplateId(String templateId) {
    List<String> templateInstancesIds = new ArrayList<>();

    QueryBuilder templateIdQuery = termQuery(INFO_IS_BASED_ON, templateId);

    //logger.info("Search query: " + templateIdQuery.toString());

    SearchResponse scrollResp = client.prepareSearch(elasticsearchConfig.getIndexes().getSearchIndex().getName())
        .setQuery(templateIdQuery).setScroll(scrollTimeout).setSize(scrollLimit).get();

    while (scrollResp.getHits().getHits().length != 0) { // Zero hits mark the end of the scroll and the while loop
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        templateInstancesIds.add(hit.getSourceAsMap().get(DOCUMENT_CEDAR_ID).toString());
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(scrollTimeout).execute().actionGet();
    }
    return templateInstancesIds;
  }

  public List<String> getTemplateIds() {
    List<String> templateIds = new ArrayList<>();

    QueryBuilder templateIdsQuery = termQuery("info.nodeType", "template");

    SearchResponse scrollResp = client.prepareSearch(elasticsearchConfig.getIndexes().getSearchIndex().getName())
        .setQuery(templateIdsQuery).setScroll(scrollTimeout).setSize(scrollLimit).get();

    while (scrollResp.getHits().getHits().length != 0) { // Zero hits mark the end of the scroll and the while loop
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        templateIds.add(hit.getSourceAsMap().get(DOCUMENT_CEDAR_ID).toString());
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

      for (EsRule rule : rules) {
        // either use client#prepare, or use Requests# to directly build index/delete requests
        bulkRequest.add(client.prepareIndex(
            elasticsearchConfig.getIndexes().getRulesIndex().getName(),
            IndexedDocumentType.DOC.getValue()).setSource(mapper.convertValue(rule, Map.class))
        );
      }

      BulkResponse bulkResponse = bulkRequest.get();
      if (bulkResponse.hasFailures()) {
        // process failures by iterating through each bulk response item
        logger.error("Failure when processing bulk request:");
        logger.error(bulkResponse.buildFailureMessage());
      }
    } else {
      logger.warn("There are no rules to index");
    }
  }

  /**
   * Remove all indexed rules
   */
  public void removeAllRules() {
    BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(client)
        .filter(QueryBuilders.matchAllQuery())
        .source(elasticsearchConfig.getIndexes().getRulesIndex().getName()).get();
    long deleted = response.getDeleted();
    logger.info("No. rules removed: " + deleted);
  }

  /**
   * Count the number of rules in the index. If templateId is provided, it counts the rules for a given template.
   * Otherwise, it counts the total number of rules in the index.
   *
   * @param templateId
   * @return
   */
  public long getNumberOfRules(String templateId) {

    SearchRequestBuilder requestBuilder =
        client.prepareSearch(elasticsearchConfig.getIndexes().getRulesIndex().getName())
        .setTypes(IndexedDocumentType.DOC.getValue());

    if (templateId != null && !templateId.isEmpty()) {
      requestBuilder.setQuery(QueryBuilders.termQuery(INDEX_TEMPLATE_ID, templateId));
    } else {
      requestBuilder.setQuery(QueryBuilders.matchAllQuery()); // return all rules in the index
    }
    requestBuilder.setSize(0); // Don't return any documents, we don't need them.
    return requestBuilder.get().getHits().getTotalHits(); // Execute query and count results
  }


}