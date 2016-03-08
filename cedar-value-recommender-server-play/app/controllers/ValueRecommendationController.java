package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//@Api(value = "/", description = "Value Suggestion Server")
public class ValueRecommendationController extends Controller {

  public static ValueRecommenderService suggestionService;

  static {
    suggestionService = new ValueRecommenderService();
  }

  public static Result recommendValues() {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode input = request().body().asJson();
    Recommendation recommendation;
    JsonNode output;
    try {
      List<Field> populatedFields = new ArrayList<>();
      if (input.get("populatedFields") != null) {
        populatedFields = mapper.readValue(input.get("populatedFields").traverse(),
            mapper.getTypeFactory().constructCollectionType(List.class, Field.class));
      }
      Field targetField = mapper.readValue(input.get("targetField").traverse(), Field.class);
      recommendation =
          suggestionService.getRecommendation(populatedFields, targetField);
      output = mapper.valueToTree(recommendation);

    } catch (IOException e) {
      return badRequest();
    }
    return ok(output);
  }

}