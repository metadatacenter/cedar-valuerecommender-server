package org.metadatacenter.cedar.valuerecommender;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.valuerecommender.resources.CommandResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.RulesGenerationStatusManager;
import org.metadatacenter.util.test.RouteSurface;
import org.metadatacenter.util.test.TestAuthUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Route safety net: probes every endpoint the value-recommender command resource declares,
 * unauthenticated, and requires each to answer 401. A 404/405 means the route vanished or changed
 * verb; any other status means an endpoint lost its authentication assertion. No fixtures and no
 * backend are involved.
 */
public class ValueRecommenderRoutesRespondTest {

  static {
    // Must run before the test support boots the server, which reads the port env vars.
    // OS-assigned ports keep concurrent test processes isolated.
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_VALUERECOMMENDER_HTTP_PORT", "0");
    environment.put("CEDAR_VALUERECOMMENDER_ADMIN_PORT", "0");
    environment.put("CEDAR_VALUERECOMMENDER_STOP_PORT", "0");
    environment.put("CEDAR_OPENSEARCH_REST_PORT", "1");
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<ValueRecommenderServerConfiguration> SERVER =
      new DropwizardTestSupport<>(ValueRecommenderServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static String authHeader;
  private static String adminAuthHeader;

  @BeforeAll
  public static void startServer() throws Exception {
    SERVER.before();
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_VALUERECOMMENDER);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
    adminAuthHeader = TestAuthUtil.getAdminUserAuthHeader(cedarConfig);
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

  @Test
  public void unavailableOpenSearchReturnsSanitizedServiceUnavailable() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort()
            + "/command/can-generate-recommendations"))
        .header("Authorization", authHeader)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();

    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    Assertions.assertEquals(503, response.statusCode(), response.body());
    JsonNode error = JsonMapper.STRICT_MAPPER.readTree(response.body());
    Assertions.assertEquals("SERVICE_UNAVAILABLE", error.path("status").asText(), response.body());
    Assertions.assertEquals("OpenSearch is unavailable", error.path("message").asText(), response.body());
    Assertions.assertTrue(error.path("originalException").isMissingNode()
        || error.path("originalException").isNull(), response.body());
    Assertions.assertTrue(error.path("sourceException").isMissingNode()
        || error.path("sourceException").isNull(), response.body());
    Assertions.assertFalse(response.body().contains("127.0.0.1"), response.body());
  }

  @Test
  public void statusEndpointPublishesCompleteSnapshotsAcrossRepeatedGenerations() throws Exception {
    String id = "https://repo.metadatacenter.orgx/templates/" + UUID.randomUUID();
    RulesGenerationStatusManager.started(id, 3);
    JsonNode processing = readStatus(id);
    Assertions.assertEquals("PROCESSING", processing.path("status").asText());
    Assertions.assertEquals(3, processing.path("templateInstancesCount").asInt());

    RulesGenerationStatusManager.completed(id, 2);
    JsonNode completed = readStatus(id);
    Assertions.assertEquals("COMPLETED", completed.path("status").asText());
    Assertions.assertEquals(2, completed.path("rulesIndexedCount").asInt());
    Assertions.assertTrue(completed.hasNonNull("finishTime"));

    RulesGenerationStatusManager.started(id, 4);
    JsonNode restarted = readStatus(id);
    Assertions.assertEquals("PROCESSING", restarted.path("status").asText());
    Assertions.assertEquals(4, restarted.path("templateInstancesCount").asInt());
    Assertions.assertFalse(restarted.hasNonNull("finishTime"));
    Assertions.assertFalse(restarted.hasNonNull("rulesIndexedCount"));
    RulesGenerationStatusManager.completed(id, 0);
  }

  private JsonNode readStatus(String id) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + "/command/generate-rules/status"))
        .header("Authorization", adminAuthHeader).GET().build();
    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    Assertions.assertEquals(200, response.statusCode(), response.body());
    JsonNode statuses = JsonMapper.STRICT_MAPPER.readTree(response.body());
    Assertions.assertTrue(statuses.isArray(), response.body());
    JsonNode match = null;
    for (JsonNode status : statuses) {
      Assertions.assertTrue(status.isObject(), "A status snapshot must never contain a null row: " + response.body());
      Assertions.assertTrue(status.hasNonNull("templateId"), response.body());
      Assertions.assertTrue(status.hasNonNull("status"), response.body());
      if (id.equals(status.path("templateId").asText())) match = status;
    }
    Assertions.assertNotNull(match, response.body());
    return match;
  }

}
