package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
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

//@Api(value = "/", description = "Value Suggestion Server")
public class ValueRecommendationController extends Controller {

  public static ValueRecommenderService recommenderService;

  static {
    recommenderService = new ValueRecommenderService();
  }

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