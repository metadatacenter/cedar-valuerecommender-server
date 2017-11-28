package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class CedarUtils {

  /**
   * Returns basic information of all template nodes (only for elements and fields)
   *
   * @param template
   * @param currentPath Used internally to store the current node path
   * @param results     Used internally to store the results
   * @return A list all template elements and fields represented using the TemplateNode class
   */
  public static List<TemplateNode> getTemplateNodes(JsonNode template, List<String> currentPath, List results) {
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
        boolean isArray;
        // Single-instance node
        if (!field.getValue().has(ITEMS_FIELD_NAME)) {
          fieldNode = field.getValue();
          isArray = false;
        }
        // Multi-instance node
        else {
          fieldNode = field.getValue().get(ITEMS_FIELD_NAME);
          isArray = true;
        }
        // Field
        if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType
            .FIELD.getAtType())) {
          boolean isValueRecommendationEnabled = false;
          if (fieldNode.get(UI_FIELD_NAME) != null && fieldNode.get(UI_FIELD_NAME).get(RECOMMENDATION_ENABLED_FIELD_NAME) != null) {
            if (fieldNode.get(UI_FIELD_NAME).get(RECOMMENDATION_ENABLED_FIELD_NAME).asBoolean() == true) {
             isValueRecommendationEnabled = true;
            }
          }
          // Add field path to the results. I create a new list to not modify currentPath
          List<String> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(fieldKey);
          results.add(new TemplateNode(fieldKey, fieldPath, CedarNodeType.FIELD, isArray, isValueRecommendationEnabled));
        }
        // Element
        else if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals
            (CedarNodeType.ELEMENT.getAtType())) {
          List<String> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(fieldKey);
          results.add(new TemplateNode(fieldKey, fieldPath, CedarNodeType.ELEMENT, isArray, null));
          getTemplateNodes(fieldNode, fieldPath, results);
        }
        // All other nodes
        else {
          getTemplateNodes(fieldNode, currentPath, results);
        }
      }
    }
    return results;
  }

  /**
   * Returns the value of a given field.
   * @param node
   * @param concatStringValue For @id values (ontology terms), it also concatenates the rdfs:label
   * @return The value of the field
   */
  public static Optional<String> getValueOfField(Map node, boolean concatStringValue) {
    if (node.containsKey(VALUE_FIELD_NAME) && node.get(VALUE_FIELD_NAME) != null) {
      return Optional.of(node.get(VALUE_FIELD_NAME).toString());
    } else if (node.containsKey(ID_FIELD_NAME) && node.get(ID_FIELD_NAME) != null) {
      if (concatStringValue) {
        if (node.containsKey(LABEL_FIELD_NAME) && node.get(LABEL_FIELD_NAME) != null) {
          // TODO: fix to store the ontology term URI
          //return Optional.of(node.get(ID_FIELD_NAME).toString() + "|" + node.get(LABEL_FIELD_NAME).toString());
          return Optional.of(node.get(LABEL_FIELD_NAME).toString());
        }
      }
      return Optional.of(node.get(ID_FIELD_NAME).toString());
    } else {
      return Optional.empty();
    }
  }

}
