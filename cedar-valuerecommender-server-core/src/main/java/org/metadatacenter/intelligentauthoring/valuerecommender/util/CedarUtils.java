package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public class CedarUtils {

  private static final String VALUE_FIELD_NAME = "@value";
  private static final String ID_FIELD_NAME = "@id";

  /**
   * Checks if a given node from a template instance corresponds to a template field
   * @param node
   */
  public static boolean isTemplateFieldInstance(JsonNode node) {
    //if (getValueOfField(node).isPresent()
    // TODO...
    return true;
  }

  /**
   * Returns the value of a given field.
   * @param node
   * @return
   */
  public static Optional<String> getValueOfField(JsonNode node) {
    if (node.has(VALUE_FIELD_NAME)) {
      return Optional.of(node.get(VALUE_FIELD_NAME).textValue());
    } else if (node.has(ID_FIELD_NAME)) {
      return Optional.of(node.get(ID_FIELD_NAME).textValue());
    } else {
      return Optional.empty();
    }
  }

}
