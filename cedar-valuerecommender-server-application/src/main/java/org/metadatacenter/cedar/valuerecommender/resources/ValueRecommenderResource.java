package org.metadatacenter.cedar.valuerecommender.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import io.swagger.annotations.*;
import org.metadatacenter.cedar.valuerecommender.utils.Validator;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.cedar.valuerecommender.utils.Constants.RECOMMEND_VALUES_SCHEMA_FILE;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Api(value = "/valuerecommender", description = "Value Recommender Server")
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ValueRecommenderResource {

  protected
  @Context
  UriInfo uriInfo;

  protected
  @Context
  HttpServletRequest request;

  protected
  @Context
  HttpServletResponse response;

  private static ValueRecommenderService valueRecommenderService;

  public static void injectValueRecommenderService(ValueRecommenderService valueRecommenderService) {
    ValueRecommenderResource.valueRecommenderService = valueRecommenderService;
  }

  @ApiOperation(
      value = "Checks if there are template instances indexed for a particular template")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @GET
  @Timed
  @Path("/has-instances")
  public Response hasInstances(@ApiParam(value = "Template identifier", required = true) @QueryParam
      ("template_id") String templateId) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    if (templateId.isEmpty()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("templateId", templateId)
          .errorMessage("The template_id cannot be empty")
          .build();

    }
    JsonNode output;
    try {
      boolean result = valueRecommenderService.hasInstances(templateId);
      output = JsonMapper.MAPPER.valueToTree(result);
    } catch (Exception e) {
      throw new CedarAssertionException(e);
    }
    return Response.ok().entity(output).build();
  }

  @ApiOperation(
      value = "Get value recommendations for metadata template fields",
      httpMethod = "POST")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(value = "Populated fields and target field", required = true,
          defaultValue = "{\n" +
              "\t\"populatedFields\": [{\n" +
              "\t\t\"name\": \"gse['@value']\",\n" +
              "\t\t\"value\": \"GSE1\"\n" +
              "\t}],\n" +
              "\t\"targetField\": {\n" +
              "\t\t\"name\": \"sampleTitle['@value']\"\n" +
              "\t}\n" +
              "}", paramType = "body")})
  @Path("/recommend")
  @POST
  public Response recommendValues() throws CedarAssertionException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    JsonNode input = c.request().getRequestBody().asJson();
    ObjectMapper mapper = new ObjectMapper();
    Recommendation recommendation;
    JsonNode output = null;
    try {
      // Input validation against JSON schema
      ProcessingReport validationReport = Validator.validateInput(input, RECOMMEND_VALUES_SCHEMA_FILE);
      if (!validationReport.isSuccess()) {
        String validationMsg = Validator.extractProcessingReportMessages(validationReport);
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .errorMessage(validationMsg)
            .build();
      }
      String templateId = null;
      if (input.get("templateId") != null) {
        templateId = input.get("templateId").asText();
      }
      List<Field> populatedFields = new ArrayList<>();
      if (input.get("populatedFields") != null) {
        populatedFields = mapper.readValue(input.get("populatedFields").traverse(),
            mapper.getTypeFactory().constructCollectionType(List.class, Field.class));
      }
      Field targetField = mapper.readValue(input.get("targetField").traverse(), Field.class);
      recommendation = valueRecommenderService.getRecommendation(templateId, populatedFields, targetField);
      output = mapper.valueToTree(recommendation);
    }
    catch (IllegalArgumentException e) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage(e.getMessage())
          .build();
    }
    catch (Exception e) {
      throw new CedarAssertionException(e);
    }
    return Response.ok().entity(output).build();
  }

}