package org.metadatacenter.cedar.valuerecommender.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import org.metadatacenter.cedar.valuerecommender.utils.Validator;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.io.CanGenerateRecommendationsStatus;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import org.metadatacenter.util.http.CedarResponse;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
public class CommandResource extends AbstractValuerecommenderServerResource {

  private static ValueRecommenderService valueRecommenderService;

  public CommandResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  public static void injectServices(ValueRecommenderService valueRecommenderService) {
    CommandResource.valueRecommenderService = valueRecommenderService;
  }

  /**
   * Recommend values for a target field <br/>
   * Input parameters: described at "recommendValues-schema.json"
   */
  @POST
  @Timed
  @Path("/recommend")
  public Response recommendValues() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    JsonNode input = c.request().getRequestBody().asJson();
    ObjectMapper mapper = new ObjectMapper();
    Recommendation recommendation;
    JsonNode output = null;
    try {
      // Input validation against JSON schema
      ProcessingReport validationReport = Validator.validateInput(input);
      if (!validationReport.isSuccess()) {
        String validationMsg = Validator.extractProcessingReportMessages(validationReport);
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .errorMessage(validationMsg)
            .build();
      }
      String templateId = null;
      if (input.get(INPUT_TEMPLATE_ID) != null) {
        templateId = input.get(INPUT_TEMPLATE_ID).asText();
      }
      List<Field> populatedFields = new ArrayList<>();
      if (input.get(INPUT_POPULATED_FIELDS) != null) {
        populatedFields = mapper.readValue(input.get(INPUT_POPULATED_FIELDS).traverse(),
            mapper.getTypeFactory().constructCollectionType(List.class, Field.class));
      }
      Field targetField = mapper.readValue(input.get(INPUT_TARGET_FIELD).traverse(), Field.class);

      boolean strictMatch = false;
      if (input.get(INPUT_STRICT_MATCH) != null) {
        strictMatch = input.get(INPUT_STRICT_MATCH).asBoolean();
      }

      boolean includeDetails = false;
      if (input.get(INPUT_INCLUDE_DETAILS) != null) {
        includeDetails = input.get(INPUT_INCLUDE_DETAILS).asBoolean();
      }
      recommendation = valueRecommenderService.getRecommendation(templateId, populatedFields, targetField,
          strictMatch, FILTER_BY_RECOMMENDATION_SCORE, FILTER_BY_CONFIDENCE, FILTER_BY_SUPPORT, USE_MAPPINGS,
          includeDetails);

      output = mapper.valueToTree(recommendation);
    } catch (IllegalArgumentException e) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage(e.getMessage())
          .build();
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
    return Response.ok().entity(output).build();
  }

  /**
   * Generates the mining rules that the value recommender will use to generate the recommendations.
   * TODO: Think about the best strategy to invoke the rules generation process (e.g., use a cron job?,
   * generate the rules and index them in Elasticsearch when a new instance is created/updated/deleted?<br/>
   *
   * <ul>Input parameters:
   * <li>templateId (optional): template used to generate the rules</li></ul>
   */
  @POST
  @Timed
  @Path("/generate-rules/{id}")
  public Response generateRules(@PathParam(PP_ID) String templateId) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    List<String> templateIds = new ArrayList<>(Collections.singletonList(templateId));
    // Run the rules generation process in a new thread
    Executors.newSingleThreadExecutor().submit(() -> valueRecommenderService.generateRules(templateIds));
    return Response.ok().build();
  }

  /**
   * This method checks if the value recommender can generate recommendations for a template. This call is
   * used by the Template Editor to enable or disable recommendations for a given template, before making multiple
   * (and probably more expensive) calls, one per field, to generate recommendations.
   * If templateId is provided, it checks if there are rules for that template. Otherwise, it checks if there are any
   * rules in the system and returns "true" unless the rules-index is empty. This case is useful for cross-template
   * recommendations.
   */
  @POST
  @Timed
  @Path("/can-generate-recommendations")
  public Response areRecommendationsEnabled() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    JsonNode input = c.request().getRequestBody().asJson();
    try {
      String templateId = null;
      if (input.get(INPUT_TEMPLATE_ID) != null) {
        templateId = input.get(INPUT_TEMPLATE_ID).asText();
      }
      CanGenerateRecommendationsStatus status = valueRecommenderService.canGenerateRecommendations(templateId);
      return Response.ok().entity(status).build();
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  /**
   * Returns status information about the rule generation process
   */
  @GET
  @Timed
  @Path("/generate-rules/status/{id}")
  public Response getRulesGenerationStatus(@PathParam(PP_ID) String templateId) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    RulesGenerationStatus status = valueRecommenderService.getRulesGenerationStatus(templateId);
    return Response.ok().entity(status).build();
  }

  /**
   * Returns status information about the rule generation process
   */
  @GET
  @Timed
  @Path("/generate-rules/status")
  public Response getRulesGenerationStatusAll() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    List<RulesGenerationStatus> status = valueRecommenderService.getRulesGenerationStatus();
    return Response.ok().entity(status).build();
  }
}
