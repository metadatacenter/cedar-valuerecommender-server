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
  public static List<TemplateNode> getTemplateNodes(JsonNode template, List<String> currentPath, List results) throws
      Exception {
    if (currentPath == null) {
      currentPath = new ArrayList<>();
    }
    if (results == null) {
      results = new ArrayList();
    }
    Iterator<Map.Entry<String, JsonNode>> jsonFieldsIterator = template.fields();
    while (jsonFieldsIterator.hasNext()) {
      Map.Entry<String, JsonNode> jsonField = jsonFieldsIterator.next();
      final String jsonFieldKey = jsonField.getKey();
      if (jsonField.getValue().isContainerNode()) {
        JsonNode jsonFieldNode;
        boolean isArray;
        // Single-instance node
        if (!jsonField.getValue().has(ITEMS_FIELD_NAME)) {
          jsonFieldNode = jsonField.getValue();
          isArray = false;
        }
        // Multi-instance node
        else {
          jsonFieldNode = jsonField.getValue().get(ITEMS_FIELD_NAME);
          isArray = true;
        }
        // Field or Element
        if (isTemplateFieldNode(jsonFieldNode) || isTemplateElementNode(jsonFieldNode)) {

          // Get field/element identifier
          String id = null;
          if ((jsonFieldNode.get(ID_FIELD_NAME) != null) && (jsonFieldNode.get(ID_FIELD_NAME).asText().length() > 0)) {
            id = jsonFieldNode.get(ID_FIELD_NAME).asText();
          } else {
            throw (new Exception(ID_FIELD_NAME + " not found for template field"));
          }

          // Add json field path to the results. I create a new list to not modify currentPath
          List<String> jsonFieldPath = new ArrayList<>(currentPath);
          jsonFieldPath.add(jsonFieldKey);

          // Field
          if (isTemplateFieldNode(jsonFieldNode)) {
            boolean isValueRecommendationEnabled = false;
            if (jsonFieldNode.get(UI_FIELD_NAME) != null && jsonFieldNode.get(UI_FIELD_NAME).get
                (RECOMMENDATION_ENABLED_FIELD_NAME) != null) {
              if (jsonFieldNode.get(UI_FIELD_NAME).get(RECOMMENDATION_ENABLED_FIELD_NAME).asBoolean() == true) {
                isValueRecommendationEnabled = true;
              }
            }
            results.add(new TemplateNode(id, jsonFieldKey, jsonFieldPath, CedarNodeType.FIELD, isArray,
                isValueRecommendationEnabled));
          }
          // Element
          else if (isTemplateElementNode(jsonFieldNode)) {
            results.add(new TemplateNode(id, jsonFieldKey, jsonFieldPath, CedarNodeType.ELEMENT, isArray, null));
            getTemplateNodes(jsonFieldNode, jsonFieldPath, results);
          }
        }
        // All other nodes
        else {
          getTemplateNodes(jsonFieldNode, currentPath, results);
        }
      }
    }
    return results;
  }

  /**
   * Returns the value of a given field.
   *
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

  /**
   * Checks if a Json node corresponds to a CEDAR template field
   * @param node
   * @return
   */
  public static boolean isTemplateFieldNode(JsonNode node) {
    if (node.get(TYPE_FIELD_NAME) != null && node.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.FIELD.getAtType())) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Checks if a Json node corresponds to a CEDAR template element
   * @param node
   * @return
   */
  public static boolean isTemplateElementNode(JsonNode node) {
    if (node.get(TYPE_FIELD_NAME) != null && node.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.ELEMENT.getAtType())) {
      return true;
    }
    else {
      return false;
    }
  }


}
