package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.cedar.valuerecommender.health.ValueRecommenderServerHealthCheck;
import org.metadatacenter.cedar.valuerecommender.resources.IndexResource;
import org.metadatacenter.cedar.valuerecommender.resources.ValueRecommenderResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.search.IndexedDocumentType;

public class ValueRecommenderServerApplication extends
    CedarMicroserviceApplication<ValueRecommenderServerConfiguration> {

  protected static ValueRecommenderService valueRecommenderService;

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
    //TODO: Value recommender is disabled here
    //ElasticsearchConfig esc = cedarConfig.getElasticsearchConfig();
    /*valueRecommenderService = new ValueRecommenderService(
        esc.getClusterName(),
        esc.getHost(),
        esc.getIndexName(),
        esc.getType(IndexedDocumentType.DOC),
        esc.getTransportPort(),
        esc.getSize());*/

    //ValueRecommenderResource.injectValueRecommenderService(valueRecommenderService);
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
