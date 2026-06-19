package org.metadatacenter.cedar.valuerecommender.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.metadatacenter.cedar.valuerecommender.resources.swaggermodel.RecommendationInput;
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

import static org.metadatacenter.constant.CedarPathParameters.PP_TEMPLATE_ID;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/command", tags = "Command", authorizations = {@Authorization("api_key")})
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
  @ApiOperation(value = "Get recommendation", notes = "Get metadata recommendations for a target field.")
  @ApiImplicitParams({
      @ApiImplicitParam(name = "Input", value = "The recommendation input", required = true,
          dataType = "org.metadatacenter.cedar.valuerecommender.resources.swaggermodel.RecommendationInput",
          paramType = "body")
  })
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
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
  @Path("/generate-rules/{template_id}")
  @ApiOperation(value = "Generate rules for a template", notes = "Generate the mining rules that the value "
      + "recommender uses to produce recommendations for a template.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response generateRules(
      @ApiParam(value = "Template identifier.", required = true)
      @PathParam(PP_TEMPLATE_ID) String templateId) throws CedarException {
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
  @ApiOperation(value = "Check whether recommendations can be generated", notes = "Check whether the value "
      + "recommender can generate recommendations for a template (or for any template if none is provided).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
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
  @Path("/generate-rules/status/{template_id}")
  @ApiOperation(value = "Get rule-generation status for a template", notes = "Get status information about the "
      + "rule-generation process for a template.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response getRulesGenerationStatus(
      @ApiParam(value = "Template identifier.", required = true)
      @PathParam(PP_TEMPLATE_ID) String templateId) throws CedarException {
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
  @ApiOperation(value = "Get rule-generation status for all templates", notes = "Get status information about the "
      + "rule-generation process for all templates.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response getRulesGenerationStatusAll() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    List<RulesGenerationStatus> status = valueRecommenderService.getRulesGenerationStatus();
    return Response.ok().entity(status).build();
  }
}
