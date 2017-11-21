package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class CedarUtils {

  /**
   * Returns the paths (using JsonPath syntax) to all fields in a template
   */
  public static List<FieldPath> getTemplateFieldPaths(JsonNode template, List<InstanceNode> currentPath, List results) {
    if (currentPath == null) {
      currentPath = new ArrayList<>();
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
        InstanceNode.NodeType nodeType = null;
        // Single-instance node
        if (!field.getValue().has(ITEMS_FIELD_NAME)) {
          fieldNode = field.getValue();
          nodeType = InstanceNode.NodeType.OBJECT;
        }
        // Multi-instance node
        else {
          fieldNode = field.getValue().get(ITEMS_FIELD_NAME);
          nodeType = InstanceNode.NodeType.ARRAY;
        }
        // Field
        if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.FIELD.getAtType())) {
          // Add field path to the results. I create a new list to not modify currentPath
          List<InstanceNode> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(new InstanceNode(fieldKey, nodeType));
          results.add(new FieldPath(fieldPath));
        }
        // Element
        else if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals
            (CedarNodeType.ELEMENT.getAtType())) {
          currentPath.add(new InstanceNode(fieldKey, nodeType));
          getTemplateFieldPaths(fieldNode, new FieldPath(currentPath).getPathList(), results);
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
