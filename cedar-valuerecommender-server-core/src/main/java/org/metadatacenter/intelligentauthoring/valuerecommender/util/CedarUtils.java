package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.ArffAttributeValue;
import org.metadatacenter.model.CedarNodeType;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
            // Get instance type (@type) if it exists)
            Optional<String> instanceType = getInstanceType(jsonFieldNode);

            boolean isValueRecommendationEnabled = false;
            if (jsonFieldNode.get(UI_FIELD_NAME) != null && jsonFieldNode.get(UI_FIELD_NAME).get
                (RECOMMENDATION_ENABLED_FIELD_NAME) != null) {
              if (jsonFieldNode.get(UI_FIELD_NAME).get(RECOMMENDATION_ENABLED_FIELD_NAME).asBoolean() == true) {
                isValueRecommendationEnabled = true;
              }
            }
            results.add(new TemplateNode(id, jsonFieldKey, jsonFieldPath, CedarNodeType.FIELD, instanceType, isArray,
                isValueRecommendationEnabled));
          }
          // Element
          else if (isTemplateElementNode(jsonFieldNode)) {
            results.add(new TemplateNode(id, jsonFieldKey, jsonFieldPath, CedarNodeType.ELEMENT, Optional.empty(),
                isArray, null));
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
   * @return The value of the field
   */
  public static Optional<ArffAttributeValue> getValueOfField(Map node) throws IOException {
    if (containsValidValue(node, VALUE_FIELD_NAME)) {
      return Optional.of(new ArffAttributeValue(node.get(VALUE_FIELD_NAME).toString(), Optional.empty()));
    } else if (containsValidValue(node, ID_FIELD_NAME)) {
      if (containsValidValue(node, LABEL_FIELD_NAME)) {
        ArffAttributeValue value = new ArffAttributeValue(node.get(ID_FIELD_NAME).toString(),
            Optional.of(node.get(LABEL_FIELD_NAME).toString()));
        return Optional.of(value);
      }
      else {
        throw new IOException("There is no label for ontology term: " + node.get(ID_FIELD_NAME).toString());
      }
    } else {
      return Optional.empty();
    }
  }

  /**
   * Checks if a Json node corresponds to a CEDAR template field
   *
   * @param node
   * @return
   */
  public static boolean isTemplateFieldNode(JsonNode node) {
    if (node.get(TYPE_FIELD_NAME) != null && node.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.FIELD.getAtType
        ())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks if a Json node corresponds to a CEDAR template element
   *
   * @param node
   * @return
   */
  public static boolean isTemplateElementNode(JsonNode node) {
    if (node.get(TYPE_FIELD_NAME) != null && node.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType.ELEMENT
        .getAtType())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks if a map contains a valid String value
   * @param map
   * @param key
   * @return
   */
  public static boolean containsValidValue(Map map, String key) {
    if (map.containsKey(key) && map.get(key) != null && map.get(key).toString().trim().length() > 0) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns the instance type of a field node
   * @param fieldNode
   * @return
   */
  public static Optional<String> getInstanceType(JsonNode fieldNode) {
    if (isTemplateFieldNode(fieldNode)) {
      if (fieldNode.get(PROPERTIES_FIELD_NAME) != null &&
          fieldNode.get(PROPERTIES_FIELD_NAME).get(TYPE_FIELD_NAME) != null &&
          fieldNode.get(PROPERTIES_FIELD_NAME).get(TYPE_FIELD_NAME).get(ONEOF_FIELD_NAME) != null &&
          fieldNode.get(PROPERTIES_FIELD_NAME).get(TYPE_FIELD_NAME).get(ONEOF_FIELD_NAME).size() > 0 &&
          fieldNode.get(PROPERTIES_FIELD_NAME).get(TYPE_FIELD_NAME).get(ONEOF_FIELD_NAME).get(0).get(ENUM_FIELD_NAME) != null &&
          fieldNode.get(PROPERTIES_FIELD_NAME).get(TYPE_FIELD_NAME).get(ONEOF_FIELD_NAME).get(0).get(ENUM_FIELD_NAME).size() > 0) {

        return Optional.of(fieldNode.get(PROPERTIES_FIELD_NAME).
            get(TYPE_FIELD_NAME).get(ONEOF_FIELD_NAME).get(0).get(ENUM_FIELD_NAME).get(0).asText());
      }
    }
    return Optional.empty();
  }

  /**
   * Extracts the original term URI from a BioPortal term URI
   * Example: Input: http://data.bioontology.org/ontologies/EFO/classes/http%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2FEFO_0000322
   *          Output: http://www.ebi.ac.uk/efo/EFO_0000322
   * @param termUri
   * @return
   */
  public static String getTermPreferredUri(String termUri) throws UnsupportedEncodingException {
    if (termUri.contains(BIOPORTAL_API_BASE)) {
      int index = termUri.indexOf(BIOPORTAL_API_CLASSES_FRAGMENT) + BIOPORTAL_API_CLASSES_FRAGMENT.length();
      String termPrefUri = termUri.substring(index);
      return URLDecoder.decode(termPrefUri, "UTF-8");
    }
    else {
      return termUri;
    }
  }

  /**
   * Basic test to check if a string corresponds to a URI (just for http and https)
   * @return
   */
  public static boolean isUri(String value) {
    value = value.toLowerCase();
    if (value.startsWith("http://") || value.startsWith("https://")) {
      return true;
    }
    else {
      return false;
    }
  }


}
