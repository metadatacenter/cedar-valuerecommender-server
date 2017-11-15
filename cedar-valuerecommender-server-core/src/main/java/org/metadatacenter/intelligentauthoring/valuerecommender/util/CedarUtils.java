package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class CedarUtils {

  /**
   * Returns the paths (using JsonPath syntax) to all fields in a template
   */
  public static List<String> getTemplateFieldPaths(JsonNode template, String currentPath, List results) {
    if (currentPath == null) {
      currentPath = "";
    }
    if (results == null) {
      results = new ArrayList();
    }
    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = template.fields();
    while (fieldsIterator.hasNext()) {
      Map.Entry<String, JsonNode> field = fieldsIterator.next();
      final String fieldKey = field.getKey();
      if (field.getValue().isContainerNode()) {
        JsonNode fieldNode;
        // Single-instance fields
        if (!field.getValue().has(ITEMS_FIELD_NAME)) {
          fieldNode = field.getValue();
        }
        // Multi-instance fields
        else {
          fieldNode = field.getValue().get(ITEMS_FIELD_NAME);
        }

        // Field
        if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType
            .FIELD.getAtType())) {
          // Add field path to the results
          results.add(generateFieldPath(currentPath, fieldKey));
        }
        // Element
        else if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals
            (CedarNodeType.ELEMENT.getAtType())) {
          getTemplateFieldPaths(fieldNode, generateFieldPath(currentPath, fieldKey), results);
        }
        // All other nodes
        else {
          getTemplateFieldPaths(fieldNode, currentPath, results);
        }
      }
    }
    return results;
  }

  /**
   * Generates the path for a given field using JsonPath syntax
   */
  private static String generateFieldPath(String path, String fieldKey) {
    String prefix = "";
    if (path != null && path.trim().length() > 0) {
      prefix = path;
    }
    return prefix + "['" + fieldKey + "']";
  }

  /**
   * Returns the value of a given field.
   *
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

  /**
   * Returns the value of a given field.
   *
   * @param node
   * @return
   */
  public static Optional<String> getValueOfField(Map node) {
    if (node.containsKey(VALUE_FIELD_NAME)) {
      return Optional.of(node.get(VALUE_FIELD_NAME).toString());
    } else if (node.containsKey(ID_FIELD_NAME)) {
      return Optional.of(node.get(ID_FIELD_NAME).toString());
    } else {
      return Optional.empty();
    }
  }

}
