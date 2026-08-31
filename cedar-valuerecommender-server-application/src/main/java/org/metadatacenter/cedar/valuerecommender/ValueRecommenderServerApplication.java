package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.metadatacenter.cedar.util.dw.CedarDependencyHealthCheck;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceIndexResource;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.cedar.valuerecommender.resources.CommandResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchServiceFactory;
import org.metadatacenter.server.search.elasticsearch.service.RulesIndexingService;
import org.metadatacenter.server.search.util.IndexUtils;

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
  protected void initializeWithBootstrap(Bootstrap<ValueRecommenderServerConfiguration> bootstrap,
                                         CedarConfig cedarConfig) {
  }

  @Override
  public void initializeApp() {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);

    RulesIndexingService rulesIndexingService = esServiceFactory.rulesIndexingService();
    ValueRecommenderService valueRecommenderService =
        new ValueRecommenderService(cedarConfig, rulesIndexingService);

    CommandResource.injectServices(valueRecommenderService);
  }

  @Override
  public void runApp(ValueRecommenderServerConfiguration configuration, Environment environment) {
    final CedarMicroserviceIndexResource index =
        new CedarMicroserviceIndexResource(cedarConfig, getServerName());
    environment.jersey().register(index);

    environment.jersey().register(new CommandResource(cedarConfig));

    // Every recommendation this server makes is a query against the rules index, so an OpenSearch
    // it cannot reach leaves it with no answer to give.
    environment.healthChecks().register("opensearch", CedarDependencyHealthCheck.gating(
        "OpenSearch", new IndexUtils(cedarConfig).getEsManagementService()::verifyConnectivity));
  }
}
