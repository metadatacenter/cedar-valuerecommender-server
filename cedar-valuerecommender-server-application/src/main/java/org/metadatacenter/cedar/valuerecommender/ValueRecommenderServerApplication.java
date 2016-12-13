package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.valuerecommender.core.CedarAssertionExceptionMapper;
import org.metadatacenter.cedar.valuerecommender.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.valuerecommender.resources.*;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

public class ValueRecommenderServerApplication extends Application<ValueRecommenderServerConfiguration> {
  public static void main(String[] args) throws Exception {
    new ValueRecommenderServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "valuerecommender-server";
  }

  @Override
  public void initialize(Bootstrap<ValueRecommenderServerConfiguration> bootstrap) {
    // Init Keycloak
    KeycloakDeploymentProvider.getInstance();
    // Init Authorization Resolver
    IAuthorizationResolver authResolver = new AuthorizationKeycloakAndApiKeyResolver();
    Authorization.setAuthorizationResolver(authResolver);
    Authorization.setUserService(CedarDataServices.getUserService());
  }

  @Override
  public void run(ValueRecommenderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    environment.jersey().register(new ValueRecommenderResource());
    
    final ValueRecommenderServerHealthCheck healthCheck = new ValueRecommenderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    environment.jersey().register(new CedarAssertionExceptionMapper());

    // Enable CORS headers
    final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

    // Configure CORS parameters
    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
    cors.setInitParameter(ALLOWED_HEADERS_PARAM,
        "X-Requested-With,Content-Type,Accept,Origin,Referer,User-Agent,Authorization");
    cors.setInitParameter(ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD,PATCH");

    // Add URL mapping
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

  }
}
