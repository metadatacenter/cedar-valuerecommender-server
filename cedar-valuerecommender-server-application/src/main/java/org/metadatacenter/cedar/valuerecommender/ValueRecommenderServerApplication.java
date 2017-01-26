package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarDropwizardApplicationUtil;
import org.metadatacenter.cedar.valuerecommender.health.ValueRecommenderServerHealthCheck;
import org.metadatacenter.cedar.valuerecommender.resources.IndexResource;
import org.metadatacenter.cedar.valuerecommender.resources.ValueRecommenderResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;

public class ValueRecommenderServerApplication extends Application<ValueRecommenderServerConfiguration> {

  protected static CedarConfig cedarConfig;
  protected static ValueRecommenderService valueRecommenderService;

  public static void main(String[] args) throws Exception {
    new ValueRecommenderServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "valuerecommender-server";
  }

  @Override
  public void initialize(Bootstrap<ValueRecommenderServerConfiguration> bootstrap) {
    cedarConfig = CedarConfig.getInstance();
    CedarDataServices.getInstance(cedarConfig);

    CedarDropwizardApplicationUtil.setupKeycloak();

    ElasticsearchConfig esc = cedarConfig.getElasticsearchConfig();
    valueRecommenderService = new ValueRecommenderService(
        esc.getCluster(),
        esc.getHost(),
        esc.getIndex(),
        esc.getType(),
        esc.getTransportPort(),
        esc.getSize());

    ValueRecommenderResource.injectValueRecommenderService(valueRecommenderService);
  }

  @Override
  public void run(ValueRecommenderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    environment.jersey().register(new ValueRecommenderResource());

    final ValueRecommenderServerHealthCheck healthCheck = new ValueRecommenderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    CedarDropwizardApplicationUtil.setupEnvironment(environment);
  }
}
