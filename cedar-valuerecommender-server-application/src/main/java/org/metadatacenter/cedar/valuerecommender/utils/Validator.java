package org.metadatacenter.cedar.valuerecommender.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

public class Validator {

  private static JsonNode schema = null;

  static {
    try {
      schema = JsonMapper.MAPPER.readTree(Validator.class.getClassLoader().getResourceAsStream(Constants
          .RECOMMEND_VALUES_SCHEMA_PATH));
    } catch (IOException e) {
      //TODO: add logging
      e.printStackTrace();
    }
  }

  public static ProcessingReport validateInput(JsonNode input) throws ProcessingException {
    return validate(schema, input);
  }

  private static ProcessingReport validate(JsonNode schema, JsonNode instance) throws ProcessingException {
    JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();
    return validator.validate(schema, instance);
  }

  public static String extractProcessingReportMessages(ProcessingReport report) {
    if (!report.isSuccess()) {
      StringBuilder msg = new StringBuilder();
      for (final ProcessingMessage reportLine : report) {
        msg.append(reportLine.getMessage()).append("\n");
      }
      return msg.toString();
    }
    return null;
  }
}
