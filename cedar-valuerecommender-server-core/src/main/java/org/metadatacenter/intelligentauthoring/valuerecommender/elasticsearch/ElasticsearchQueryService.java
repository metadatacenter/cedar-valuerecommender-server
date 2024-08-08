package org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.opensearch.action.bulk.BackoffPolicy;
import org.opensearch.action.bulk.BulkProcessor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;
import static org.metadatacenter.constant.ElasticsearchConstants.INFO_IS_BASED_ON;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.INDEX_TEMPLATE_ID;

public class ElasticsearchQueryService {

  private OpensearchConfig opensearchConfig;
  private RestHighLevelClient client = null;
  private TimeValue scrollTimeout;
  private int scrollLimit = 5000;

  protected final Logger logger = LoggerFactory.getLogger(ElasticsearchQueryService.class);

  public ElasticsearchQueryService(OpensearchConfig esc) throws UnknownHostException {
    this.opensearchConfig = esc;
    this.scrollTimeout = TimeValue.timeValueMinutes(2);

    Settings settings = Settings.builder()
        .put("cluster.name", esc.getClusterName()).build();

    RestClientBuilder builder = RestClient.builder(
        new HttpHost(esc.getHost(), esc.getRestPort(), "http"));
    client = new RestHighLevelClient(builder);
  }

  public RestHighLevelClient getClient() {
    return client;
  }

  // It uses the scroll API to retrieve all results
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html
  // https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-search-scrolling.html
  public List<String> getTemplateInstancesIdsByTemplateId(String templateId) {
    List<String> templateInstancesIds = new ArrayList<>();

    QueryBuilder templateIdQuery = QueryBuilders.termQuery(INFO_IS_BASED_ON, templateId);

    SearchRequest searchRequest = new SearchRequest(opensearchConfig.getIndexes().getSearchIndex().getName());
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(templateIdQuery);
    searchSourceBuilder.size(scrollLimit);
    searchRequest.source(searchSourceBuilder);
    searchRequest.scroll(scrollTimeout);

    try {
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      while (searchResponse.getHits().getHits().length != 0) {
        for (SearchHit hit : searchResponse.getHits().getHits()) {
          templateInstancesIds.add(hit.getSourceAsMap().get(DOCUMENT_CEDAR_ID).toString());
        }

        SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
        scrollRequest.scroll(scrollTimeout);
        searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      }

      // Clear scroll context
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(searchResponse.getScrollId());
      client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

    } catch (IOException e) {
      // Handle the exception as needed
      e.printStackTrace();
    }

    return templateInstancesIds;
  }

  public List<String> getTemplateIds() {
    List<String> templateIds = new ArrayList<>();

    QueryBuilder templateIdsQuery = QueryBuilders.termQuery("info.resourceType", "template");

    SearchRequest searchRequest = new SearchRequest(opensearchConfig.getIndexes().getSearchIndex().getName());
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(templateIdsQuery);
    searchSourceBuilder.size(scrollLimit);
    searchRequest.source(searchSourceBuilder);
    searchRequest.scroll(scrollTimeout);

    try {
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      while (searchResponse.getHits().getHits().length != 0) {
        for (SearchHit hit : searchResponse.getHits().getHits()) {
          templateIds.add(hit.getSourceAsMap().get(DOCUMENT_CEDAR_ID).toString());
        }

        SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
        scrollRequest.scroll(scrollTimeout);
        searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      }

      // Clear scroll context
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(searchResponse.getScrollId());
      client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

    } catch (IOException e) {
      // Handle the exception as needed
      e.printStackTrace();
    }
    return templateIds;
  }

  /**
   * Index all rules in a single API call. This method fails when trying to index a large number of rules (> 50,000)
   * because it's sending all of them in a single request. For better performance use the "indexRulesBulkProcessor"
   * method.
   *
   * @param rules
   */
  public void indexRulesBulk(List<EsRule> rules) {
    if (rules.size() > 0) {
      BulkRequest bulkRequest = new BulkRequest();
      ObjectMapper mapper = new ObjectMapper();

      for (EsRule rule : rules) {
        Map<String, Object> ruleMap = mapper.convertValue(rule, Map.class);
        IndexRequest indexRequest = new IndexRequest(opensearchConfig.getIndexes().getRulesIndex().getName())
            .source(ruleMap);
        bulkRequest.add(indexRequest);
      }

      try {
        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          // process failures by iterating through each bulk response item
          logger.error("Failure when processing bulk request:");
          logger.error(bulkResponse.buildFailureMessage());
        }
      } catch (IOException e) {
        logger.error("Error executing bulk request", e);
      }
    } else {
      logger.warn("There are no rules to index");
    }
  }

  /**
   * Index all rules using Bulk Processor
   * Documentation: https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-bulk-processor.html
   *
   * @param rules
   */
  public void indexRulesBulkProcessor(List<EsRule> rules) {
    ObjectMapper mapper = new ObjectMapper();

    // Create bulk processor
    BulkProcessor bulkProcessor = BulkProcessor.builder(
            (request, bulkListener) -> client.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
            new BulkProcessor.Listener() {
              @Override
              public void beforeBulk(long executionId, BulkRequest request) {
                logger.info("Before bulk. Number of rules indexed: " + request.numberOfActions());
              }

              @Override
              public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                logger.info("After bulk. Any failures? " + response.hasFailures());
                if (response.hasFailures()) {
                  logger.error(response.buildFailureMessage());
                }
              }

              @Override
              public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.error("Bulk failed. Message: " + failure.getMessage(), failure);
              }
            })
        .setBulkActions(10000)
        .setBulkSize(new ByteSizeValue(5, ByteSizeUnit.MB))
        .setFlushInterval(TimeValue.timeValueSeconds(5))
        .setConcurrentRequests(1)
        .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(500), 3))
        .build();

    for (EsRule rule : rules) {
      Map<String, Object> ruleMap = mapper.convertValue(rule, Map.class);
      IndexRequest indexRequest = new IndexRequest(opensearchConfig.getIndexes().getRulesIndex().getName())
          .source(ruleMap);
      bulkProcessor.add(indexRequest);
    }

    try {
      bulkProcessor.awaitClose(10, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      logger.error("Error closing bulk processor", e);
    }
  }

  /**
   * Remove all indexed rules
   */
  public void removeAllRules() {
    DeleteByQueryRequest request = new DeleteByQueryRequest(opensearchConfig.getIndexes().getRulesIndex().getName());
    request.setQuery(QueryBuilders.matchAllQuery());
    request.setScroll(TimeValue.timeValueMinutes(2));

    try {
      BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);
      long deleted = response.getDeleted();
      logger.info("No. rules removed: " + deleted);
    } catch (IOException e) {
      logger.error("Error executing delete by query request", e);
    }
  }

  /**
   * Count the number of rules in the index. If templateId is provided, it counts the rules for a given template.
   * Otherwise, it counts the total number of rules in the index.
   *
   * @param templateId
   * @return
   */
  public long getNumberOfRules(String templateId) {
    try {
      SearchRequest searchRequest = new SearchRequest(opensearchConfig.getIndexes().getRulesIndex().getName());
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

      if (templateId != null && !templateId.isEmpty()) {
        searchSourceBuilder.query(QueryBuilders.termQuery(INDEX_TEMPLATE_ID, templateId));
      } else {
        searchSourceBuilder.query(QueryBuilders.matchAllQuery()); // return all rules in the index
      }

      searchSourceBuilder.size(0); // Don't return any documents, we don't need them.
      searchSourceBuilder.trackTotalHits(true);
      searchRequest.source(searchSourceBuilder);

      // Execute query and count results
      SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
      SearchHits hits = response.getHits();
      return hits.getTotalHits().value;
    } catch (IOException e) {
      logger.error("Error executing search request", e);
      return 0;
    }
  }

}
