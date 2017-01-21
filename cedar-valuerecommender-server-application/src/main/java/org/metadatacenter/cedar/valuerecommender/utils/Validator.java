package org.metadatacenter.cedar.valuerecommender.utils;

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

  public static ProcessingReport validateInput(JsonNode input, String schemaFileName) throws IOException, ProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    String schemaFilePath = Constants.APP_MODULE_FOLDER_NAME + "/" + Constants.INPUT_SCHEMA_PATH + "/" + schemaFileName;
    JsonNode schema = mapper.readTree(new File(schemaFilePath));
    return validate(schema, input);
  }

  /**
   *  JSON Schema Validation
   */
  public static ProcessingReport validate(JsonNode schema, JsonNode instance) throws ProcessingException {
    JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();
    return validator.validate(schema, instance);
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
