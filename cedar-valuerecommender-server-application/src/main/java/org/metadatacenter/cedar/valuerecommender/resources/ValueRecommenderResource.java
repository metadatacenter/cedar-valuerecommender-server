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
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.constant.CedarQueryParameters.QP_TEMPLATE_ID;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ValueRecommenderResource extends AbstractValuerecommenderServerResource {

  private static ValueRecommenderService valueRecommenderService;

  public ValueRecommenderResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  public static void injectValueRecommenderService(ValueRecommenderService valueRecommenderService) {
    ValueRecommenderResource.valueRecommenderService = valueRecommenderService;
  }

  @GET
  @Timed
  @Path("/has-instances")
  public Response hasInstances(@QueryParam(QP_TEMPLATE_ID) String templateId) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    if (templateId.isEmpty()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter(QP_TEMPLATE_ID, templateId)
          .errorMessage("The template_id cannot be empty")
          .build();

    }
    JsonNode output;
    try {
      templateId = CedarUrlUtil.urlDecode(templateId);
      System.out.println("**********************************");
      System.out.println("Template Id: " + templateId);
      System.out.println("**********************************");
      boolean result = valueRecommenderService.hasInstances(templateId);
      output = JsonMapper.MAPPER.valueToTree(result);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
    return Response.ok().entity(output).build();
  }

  @Path("/recommend")
  @POST
  public Response recommendValues() throws CedarException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
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

}