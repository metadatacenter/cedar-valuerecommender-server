package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class CedarUtils {

  /**
   * Returns the paths (using JsonPath syntax) to all fields in a template
   */
  public static List<Attribute> getTemplateAttributes(JsonNode template, List<Node> currentPath, List results) {
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
        Node.NodeType nodeType = null;
        // Single-instance node
        if (!field.getValue().has(ITEMS_FIELD_NAME)) {
          fieldNode = field.getValue();
          nodeType = Node.NodeType.OBJECT;
        }
        // Multi-instance node
        else {
          fieldNode = field.getValue().get(ITEMS_FIELD_NAME);
          nodeType = Node.NodeType.ARRAY;
        }
        // Field
        if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.FIELD.getAtType())) {
          // Add field path to the results. I create a new list to not modify currentPath
          List<Node> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(new Node(fieldKey, nodeType));
          results.add(new Attribute(fieldPath));
        }
        // Element
        else if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals
            (CedarNodeType.ELEMENT.getAtType())) {
          currentPath.add(new Node(fieldKey, nodeType));
          getTemplateAttributes(fieldNode, new Attribute(currentPath).getPath(), results);
        }
        // All other nodes
        else {
          getTemplateAttributes(fieldNode, currentPath, results);
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
