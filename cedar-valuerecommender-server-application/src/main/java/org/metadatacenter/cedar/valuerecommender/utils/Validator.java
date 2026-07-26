package org.metadatacenter.cedar.valuerecommender.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.format.AbstractFormat;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class Validator {

  private static JsonSchema schema = null;

  static {
    // Draft-04 with format assertion enabled and a lenient `uri` checker, reproducing the
    // acceptance behaviour of the former FGE engine (which parsed with java.net.URI).
    JsonMetaSchema v4 = JsonMetaSchema.builder(JsonMetaSchema.getV4())
        .addFormat(new LenientUriFormat())
        .build();
    JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4, builder -> builder.metaSchema(v4));
    SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
        .pathType(PathType.JSON_POINTER)
        .formatAssertionsEnabled(true)
        .build();
    try {
      JsonNode schemaNode = JsonMapper.MAPPER.readTree(
          Validator.class.getClassLoader().getResourceAsStream(Constants.RECOMMEND_VALUES_SCHEMA_PATH));
      schema = factory.getSchema(schemaNode, config);
    } catch (IOException e) {
      //TODO: add logging
      e.printStackTrace();
    }
  }

  public static Set<ValidationMessage> validateInput(JsonNode input) {
    return schema.validate(input);
  }

  public static String extractValidationMessages(Set<ValidationMessage> messages) {
    if (messages != null && !messages.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      for (ValidationMessage message : messages) {
        msg.append(message.getMessage()).append("\n");
      }
      return msg.toString();
    }
    return null;
  }

  private static final class LenientUriFormat extends AbstractFormat {

    private LenientUriFormat() {
      super("uri", "must be a valid URI");
    }

    @Override
    public boolean matches(String value) {
      if (value == null) {
        return true;
      }
      try {
        new URI(value);
        return true;
      } catch (URISyntaxException e) {
        return false;
      }
    }
  }
}
