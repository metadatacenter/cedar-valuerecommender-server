package org.metadatacenter.intelligentauthoring.valuerecommender.util.associationrules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.search.elasticsearch.document.field.CedarIndexFieldSchema;
import org.metadatacenter.server.search.elasticsearch.document.field.CedarIndexFieldValue;
import org.metadatacenter.server.search.util.IndexUtils;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.metadatacenter.constant.CedarConstants.SCHEMA_IS_BASED_ON;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.FIELD_SUFFIX;

/**
 * Utilities to generate and manage association rules using Weka
 */
public class WekaUtils {

  public static void templateToArff() {
    // 1. Extract list of field paths from the template
    // 2. Use the list of field paths to generate the instances in ARFF
  }

  /**
   * Translates a template instance to Weka's ARFF format (https://www.cs.waikato.ac.nz/ml/weka/arff.html). It returns
   * the line for the given template instance.
   * @param templateInstance
   */
  public static void templateInstanceToArff(JsonNode templateInstance, JsonNode templateSummary, JsonNode results) throws IOException {

      Iterator<Map.Entry<String, JsonNode>> fieldsIterator = templateInstance.fields();
      while (fieldsIterator.hasNext()) {
        Map.Entry<String, JsonNode> field = fieldsIterator.next();
        if (field.getValue().isContainerNode()) {
          if (!field.getKey().equals("@context")) {
            // Single value
            if (field.getValue().isObject()) {
              // If it is a Template Field (single instance)
              if (isTemplateField(field.getKey(), templateSummary)) {
                JsonNode fieldSchema = null;
                if (templateSummary != null && templateSummary.has(field.getKey() + FIELD_SUFFIX)) {
                  fieldSchema = templateSummary.get(field.getKey() + FIELD_SUFFIX);
                }
                if (fieldSchema != null) {





                  Optional<CedarIndexFieldValue> fv = valueToIndexValue(field.getValue(), fieldSchema);
                  if (fv.isPresent()) {
                    String outputFieldKey = field.getKey() + FIELD_SUFFIX;
                    ((ObjectNode) results).set(outputFieldKey, JsonMapper.MAPPER.valueToTree(fv.get()));
                  }
                }
                // It is a Template Element
              } else if (isTemplateElement(field.getKey(), schemaSummary)) {
                ((ObjectNode) results).set(field.getKey(), JsonNodeFactory.instance.objectNode());
                extractValuesSummary(nodeType, schemaSummary.get(field.getKey()), field.getValue(), results.get
                    (field.getKey()));
              }
            }
            // Array
            else if (field.getValue().isArray()) {
              ((ObjectNode) results).set(field.getKey(), JsonNodeFactory.instance.arrayNode());
              for (int i = 0; i < field.getValue().size(); i++) {
                JsonNode arrayItem = field.getValue().get(i);
                // It is a Template Field (multi-instance)
                if (isTemplateField(field.getKey(), schemaSummary)) {

                  String fieldValueName = getFieldValueName(arrayItem);

                  JsonNode fieldSchema = schemaSutemplateSummarymmary.get(field.getKey() + FIELD_SUFFIX);
                  // If the field was not found in the template, it is ignored. It may happen when template updated
                  if (fieldSchema != null) {
                    Optional<CedarIndexFieldValue> fv = valueToIndexValue(arrayItem, fieldSchema);
                    if (fv.isPresent()) {
                      ((ArrayNode) results.get(field.getKey())).add(JsonMapper.MAPPER.valueToTree(fv.get()));
                    }
                  }


                }
                // It is a Template Element (multi-instance)
                else if (isTemplateElement(field.getKey(), schemaSummary)) {
                  ((ArrayNode) results.get(field.getKey())).add(JsonNodeFactory.instance.objectNode());
                  extractValuesSummary(nodeType, schemaSummary.get(field.getKey()), arrayItem, results.get(field
                      .getKey()).get(i));
                }
              }
            }
          }
        }
      }

    return results;
  }

  // Checks if JSON the field is a template field using information from the template
  private static boolean isTemplateField(String fieldName, JsonNode schemaSummary) {
    String templateFieldName = fieldName + FIELD_SUFFIX;
    if (schemaSummary.has(templateFieldName)) {
      return true; // It is a template field
    } else {
      return false;
    }
  }

  // Checks if JSON the field is a template element using information from the template
  private static boolean isTemplateElement(String fieldName, JsonNode schemaSummary) {
    if (schemaSummary.has(fieldName)) {
      return true; // It is a template element
    } else {
      return false;
    }
  }

  private String getFieldValue(JsonNode field) {
    String fieldValueName = getFieldValueName(field);
    return field.get(fieldValueName).textValue();
  }

  private String getFieldValueName(JsonNode item) {
    if (item.has("@value")) {
      return "@value";
    }
    return "@id";
  }

  private Optional<CedarIndexFieldValue> valueToIndexValue(JsonNode valueNode, JsonNode fieldSchema) throws
      JsonProcessingException {
    CedarIndexFieldSchema fs = JsonMapper.MAPPER.treeToValue(fieldSchema, CedarIndexFieldSchema.class);
    CedarIndexFieldValue indexValue = null;

    String fieldValueName = getFieldValueName(valueNode);
    if (valueNode.has(fieldValueName)) {
      indexValue = new CedarIndexFieldValue();
      // If the field was not found in the template, it is ignored. It may happen if the template is updated
      if (!valueNode.isNull()) {
        indexValue.setFieldName(fs.getFieldName());
        // Free text value
        if (!isControlledValue(valueNode)) {
          // Set appropriate value field according to the value type
          if (fs.getFieldValueType().equals(IndexUtils.ESType.STRING.toString())) {
            // Avoid indexing the empty string
            if (valueNode.get(fieldValueName).asText().trim().length() > 0) {
              indexValue.setFieldValue_string(valueNode.get(fieldValueName).asText());
            }
          }
          // TODO: add all remaining field types
          else {
            // Avoid indexing the empty string
            if (valueNode.asText().trim().length() > 0) {
              indexValue.setFieldValue_string(valueNode.get(fieldValueName).asText());
            }
          }
        }
        // Controlled value
        else {
          // Controlled term preferred name
          JsonNode valueLabelNode = valueNode.get(URI_LABEL_FIELD);
          indexValue.setFieldValue_string(valueLabelNode.asText());
          // Term URI
          indexValue.setFieldValueSemanticType(valueNode.get(fieldValueName).asText());
          indexValue.setFieldValueAndSemanticType(indexValue.generateFieldValueAndSemanticType());
        }
      } else {
        // Do nothing. Null values will not be indexed
      }
    }
    return Optional.ofNullable(indexValue);
  }




}
