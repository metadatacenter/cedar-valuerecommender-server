package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.wordnik.swagger.annotations.*;
import org.apache.http.HttpStatus;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ErrorMsgBuilder;
import utils.Validator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static utils.Constants.VALIDATION_ERROR_MSG;
import static utils.Constants.INTERNAL_ERROR_MSG;

@Api(value = "/valuerecommender", description = "Value Recommender Server")
public class ValueRecommenderController extends Controller {

  public static ValueRecommenderService recommenderService;

  static {
    recommenderService = new ValueRecommenderService();
  }

  @ApiOperation(
      value = "Get value recommendations for metadata template fields",
      //notes = "The search scope can be specified using comma separated strings",
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
          "\t\t\"name\": \"gse._value\",\n" +
          "\t\t\"value\": \"GSE1\"\n" +
          "\t}],\n" +
          "\t\"targetField\": {\n" +
          "\t\t\"name\": \"sampleTitle._value\"\n" +
          "\t}\n" +
          "}", paramType = "body")})
  public static Result recommendValues() {
    JsonNode input = request().body().asJson();
    ObjectMapper mapper = new ObjectMapper();
    Recommendation recommendation;
    JsonNode output = null;
    try {
      // Input validation against JSON schema
      ProcessingReport validationReport = Validator.validateInput(input);
      if (!validationReport.isSuccess()) {
        String validationMsg = Validator.extractProcessingReportMessages(validationReport);
        return badRequest(ErrorMsgBuilder.build(HttpStatus.SC_BAD_REQUEST, VALIDATION_ERROR_MSG, validationMsg));
      }
      List<Field> populatedFields = new ArrayList<>();
      if (input.get("populatedFields") != null) {
        populatedFields = mapper.readValue(input.get("populatedFields").traverse(),
            mapper.getTypeFactory().constructCollectionType(List.class, Field.class));
      }
      Field targetField = mapper.readValue(input.get("targetField").traverse(), Field.class);
      recommendation =
          recommenderService.getRecommendation(populatedFields, targetField);
      output = mapper.valueToTree(recommendation);
    } catch (IOException e) {
      return internalServerError(ErrorMsgBuilder.build(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MSG, e
          .getMessage()));
    } catch (ProcessingException e) {
      return internalServerError(ErrorMsgBuilder.build(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MSG, e
          .getMessage()));
    } catch (Exception e) {
      return internalServerError(ErrorMsgBuilder.build(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MSG, e
          .getMessage()));
    }
    return ok(output);
  }

}