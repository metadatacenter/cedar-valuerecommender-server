package org.metadatacenter.cedar.valuerecommender;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.valuerecommender.resources.CommandResource;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.util.test.RouteSurface;

import java.util.HashMap;
import java.util.Map;

/**
 * Route safety net: probes every endpoint the value-recommender command resource declares,
 * unauthenticated, and requires each to answer 401. A 404/405 means the route vanished or changed
 * verb; any other status means an endpoint lost its authentication assertion. No fixtures and no
 * backend are involved.
 */
public class ValueRecommenderRoutesRespondTest {

  static {
    // Must run before the test support boots the server, which reads the port env vars. Ports are
    // distinct from the dev server and from every other booting test class.
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_VALUERECOMMENDER_HTTP_PORT", "19022");
    environment.put("CEDAR_VALUERECOMMENDER_ADMIN_PORT", "19122");
    environment.put("CEDAR_VALUERECOMMENDER_STOP_PORT", "19222");
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<ValueRecommenderServerConfiguration> SERVER =
      new DropwizardTestSupport<>(ValueRecommenderServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

  @BeforeAll
  public static void startServer() throws Exception {
    SERVER.before();
  }

  @AfterAll
  public static void stopServer() {
    SERVER.after();
  }

  @Test
  public void everyRouteRejectsAnUnauthenticatedRequest() {
    RouteSurface.assertEveryRouteAnswers(
        "http://localhost:" + SERVER.getLocalPort(),
        RouteSurface.endpoints(CommandResource.class),
        401);
  }

}
