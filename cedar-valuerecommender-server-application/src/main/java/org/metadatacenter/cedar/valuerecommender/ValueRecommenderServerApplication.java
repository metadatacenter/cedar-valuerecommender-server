package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.cedar.valuerecommender.health.ValueRecommenderServerHealthCheck;
import org.metadatacenter.cedar.valuerecommender.resources.IndexResource;
import org.metadatacenter.cedar.valuerecommender.resources.ValueRecommenderResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderServiceArm;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.search.IndexedDocumentType;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchServiceFactory;
import org.metadatacenter.server.search.elasticsearch.service.ValueRecommenderIndexingService;

public class ValueRecommenderServerApplication extends
    CedarMicroserviceApplication<ValueRecommenderServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new ValueRecommenderServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.VALUERECOMMENDER;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<ValueRecommenderServerConfiguration> bootstrap, CedarConfig
      cedarConfig) {
  }

  @Override
  public void initializeApp() {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);
    ElasticsearchConfig esc = cedarConfig.getElasticsearchConfig();

    ValueRecommenderService valueRecommenderService = new ValueRecommenderService(
        esc.getClusterName(),
        esc.getHost(),
        esc.getIndexes().getSearchIndex().getName(),
        esc.getIndexes().getSearchIndex().getType(IndexedDocumentType.CONTENT),
        esc.getTransportPort(),
        esc.getSize());
    ValueRecommenderIndexingService valueRecommenderIndexingService = esServiceFactory.valueRecommenderIndexingService();
    ValueRecommenderServiceArm valueRecommenderServiceArm =
        new ValueRecommenderServiceArm(cedarConfig, valueRecommenderIndexingService);


    ValueRecommenderResource.injectServices(valueRecommenderService, valueRecommenderServiceArm);
  }

  @Override
  public void runApp(ValueRecommenderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource(cedarConfig);
    environment.jersey().register(index);

    environment.jersey().register(new ValueRecommenderResource(cedarConfig));

    final ValueRecommenderServerHealthCheck healthCheck = new ValueRecommenderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }
}
