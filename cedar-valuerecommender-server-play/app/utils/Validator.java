package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;

import java.io.File;
import java.io.IOException;

public class Validator {

  public static ProcessingReport validateInput(JsonNode input) throws IOException, ProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode schema = mapper.readTree(getInputSchemaFile());
    return validate(schema, input);
  }

  /**
   *  JSON Schema Validation
   */
  public static ProcessingReport validate(JsonNode schema, JsonNode instance) throws ProcessingException {
    JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();
    return validator.validate(schema, instance);
  }

  private static File getInputSchemaFile() {
    String path;
    String workingDirectory = System.getProperty("user.dir");
    // Get last fragment of working directory
    String folder = workingDirectory.substring(workingDirectory.lastIndexOf("/") + 1, workingDirectory.length());
    // Working directory for Maven execution (mvn play2:run)
    if (folder.compareTo(Constants.PLAY_MODULE_FOLDER_NAME) == 0) {
      path = Constants.INPUT_SCHEMA_PATH;
    }
    // Working directory for execution from IntelliJ
    else if (folder.compareTo(Constants.PLAY_APP_FOLDER_NAME)==0) {
      path = Constants.PLAY_MODULE_FOLDER_NAME + "/" + Constants.INPUT_SCHEMA_PATH;
    }
    // Working directory for test execution from IntelliJ (working directory: ...cedar-valuerecommender-server/.idea/modules)
    else {
      path = "../../" + Constants.PLAY_MODULE_FOLDER_NAME + "/" + Constants.INPUT_SCHEMA_PATH;
    }
    return new File(path);
  }

  public static String extractProcessingReportMessages(ProcessingReport report) {
    if (!report.isSuccess()) {
      String msg = "";
      for (final ProcessingMessage reportLine : report) {
        msg += reportLine.getMessage() + "\n";
      }
      return msg;
    }
    return null;
  }
}
