package org.metadatacenter.cedar.valuerecommender.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.valuerecommender.resources.swaggermodel.RecommendationInput;
import org.metadatacenter.cedar.valuerecommender.utils.Validator;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.metadatacenter.constant.CedarPathParameters.PP_TEMPLATE_ID;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Command")
@SecurityRequirement(name = "api_key")
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
  @Operation(summary = "Get recommendation", description = "Get metadata recommendations for a target field.")
  @RequestBody(description = "The recommendation input", required = true, content = @Content(schema = @Schema(implementation = org.metadatacenter.cedar.valuerecommender.resources.swaggermodel.RecommendationInput.class)))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "OpenSearch unavailable")
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
      java.util.Set<ValidationMessage> validationReport = Validator.validateInput(input);
      if (!validationReport.isEmpty()) {
        String validationMsg = Validator.extractValidationMessages(validationReport);
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
    } catch (CedarDependencyUnavailableException e) {
      throw e;
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
  @Operation(summary = "Generate rules for a template", description = "Generate the mining rules that the value "
      + "recommender uses to produce recommendations for a template.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response generateRules(
      @Parameter(description = "Template identifier.", required = true)
      @PathParam(PP_TEMPLATE_ID) String templateId) throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    List<String> templateIds = new ArrayList<>(Collections.singletonList(templateId));
    // Run the rules generation process in a new thread
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.submit(() -> valueRecommenderService.generateRules(templateIds));
    executor.shutdown();
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
  @Operation(summary = "Check whether recommendations can be generated", description = "Check whether the value "
      + "recommender can generate recommendations for a template (or for any template if none is provided).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "503", description = "OpenSearch unavailable")
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
    } catch (CedarDependencyUnavailableException e) {
      throw e;
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
  @Operation(summary = "Get rule-generation status for a template", description = "Get status information about the "
      + "rule-generation process for a template.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response getRulesGenerationStatus(
      @Parameter(description = "Template identifier.", required = true)
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
  @Operation(summary = "Get rule-generation status for all templates", description = "Get status information about the "
      + "rule-generation process for all templates.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response getRulesGenerationStatusAll() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.RULES_INDEX_REINDEX);

    List<RulesGenerationStatus> status = valueRecommenderService.getRulesGenerationStatus();
    return Response.ok().entity(status).build();
  }
}
