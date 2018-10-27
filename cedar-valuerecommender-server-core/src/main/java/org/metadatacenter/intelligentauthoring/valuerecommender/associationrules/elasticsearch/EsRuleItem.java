package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

import org.metadatacenter.intelligentauthoring.valuerecommender.util.FieldValueResultUtils;

import java.util.List;

/**
 * This class represent each item of the premise/consequence of an association rule.
 */
public class EsRuleItem {

  private String fieldPath;
  private String fieldType;
  private String fieldNormalizedPath;
  private List<String> fieldTypeMappings;
  private String fieldValueType;
  private String fieldValueLabel;
  private String fieldNormalizedValue;
  private List<String> fieldValueMappings;
  private String fieldValueResult;

  /**
   * Attributes used to match the field requirements to the association rules: fieldNormalizedPath, fieldNormalizedValue
   * Attributes used to generate the recommendations that will be returned to the user: fieldValueType, fieldValueLabel
   *
   * @param fieldPath             Path to the field (using dot notation). It makes it possible to identify the field
   *                              in the template or element. Examples:
   *                              - For a flat template: "Tissue"
   *                              - For a nested "Sample" element: "Sample.Tissue"
   *                              - For a multi-instance field nested in a "Sample" element": "Sample.Tissue" [CONFIRM]
   * @param fieldType             URI of the ontology term that annotates the field, if any.
   * @param fieldNormalizedPath   For fields that have not been annotated with an ontology term, it is a normalized
   *                              string obtained from the fieldPath (e.g., SAMPLE.TISSUE). For fields that have been
   *                              annotated with an ontology term, it contains the term URI that annotates the field,
   *                              obtained from the fieldType (e.g., http://purl.obolibrary.org/obo/DOID_4).
   *                              Limitation: our current version does not deal neither with URI paths (e.g., URI1.URI2)
   *                              nor with "mixed" paths that contain both field names and URIs
   *                              (e.g. SAMPLE.http://purl.obolibrary.org/obo/DOID_4).
   * @param fieldTypeMappings     List of URIs equivalent to the fieldType, obtained from a mappings service.
   * @param fieldValueType        For ontology terms, this field stored the content of the @id field.
   * @param fieldValueLabel       For free text values, this field stores the content of the @value field. For
   *                              ontology terms, it stores the value of the rdfs:label field.
   * @param fieldNormalizedValue  Normalized value. For free text values, a normalized string. For ontology terms, it
   *                              contains the term URI.
   *                              <p>
   *                              Examples:
   *                              1) For: {"@value": "colorectal cancer"}
   *                              - fieldValueType = null
   *                              - fieldValueLabel = colorectal cancer
   *                              - fieldNormalizedValue = COLORECTALCANCER
   *                              2) For {"@id": "http://purl.obolibrary.org/obo/DOID_9256",
   *                              "rdfs:label": "colorectal cancer",
   *                              "@type":"http://purl.obolibrary.org/obo/DOID_4"}
   *                              - fieldType = http://purl.obolibrary.org/obo/DOID_4 ("disease" in DOID)
   *                              - fieldValueType = http://purl.obolibrary.org/obo/DOID_9256
   *                              - fieldValueLabel = colorectal cancer
   *                              - fieldNormalizedValue = http://purl.obolibrary.org/obo/DOID_9256
   * @param fieldValueMappings    List of URIs equivalent to the fieldValueType (only for annotated instances)
   * @param fieldValueResult      This field contains the information needed to generate a recommendation result. Its
   *                              String representation is used to perform the aggregation of results in Elasticsearch.
   *                              - For free-text values, fieldValueResult = [](fieldValueLabel)
   *                              - For controlled terms, fieldValueResult = [fieldValueType](fieldValueLabel)
   */
  public EsRuleItem(String fieldPath, String fieldType, String fieldNormalizedPath,
                    List<String> fieldTypeMappings, String fieldValueType,
                    String fieldValueLabel, String fieldNormalizedValue, List<String> fieldValueMappings,
                    String fieldValueResult) {
    this.fieldPath = fieldPath;
    this.fieldType = fieldType;
    this.fieldNormalizedPath = fieldNormalizedPath;
    this.fieldTypeMappings = fieldTypeMappings;
    this.fieldValueType = fieldValueType;
    this.fieldValueLabel = fieldValueLabel;
    this.fieldNormalizedValue = fieldNormalizedValue;
    this.fieldValueMappings = fieldValueMappings;
    this.fieldValueResult = fieldValueResult;
  }

  public EsRuleItem() {}

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldType() {
    return fieldType;
  }

  public String getFieldNormalizedPath() {
    return fieldNormalizedPath;
  }

  public List<String> getFieldTypeMappings() {
    return fieldTypeMappings;
  }

  public String getFieldValueType() {
    return fieldValueType;
  }

  public String getFieldValueLabel() {
    return fieldValueLabel;
  }

  public String getFieldNormalizedValue() {
    return fieldNormalizedValue;
  }

  public List<String> getFieldValueMappings() {
    return fieldValueMappings;
  }

  public String getFieldValueResult() {
    return fieldValueResult;
  }

  public String toPrettyString(boolean showNullTypes) {

    String fieldType = "";
    if (showNullTypes || getFieldType() != null) {
      fieldType = "[" + getFieldType() + "]";
    }

    String fieldValueType = "";
    if (showNullTypes || getFieldValueType() != null) {
      fieldValueType = "[" + getFieldValueType() + "]";
    }

    String stringField = fieldType + "(" + getFieldPath() + ")";
    return stringField + "=" + fieldValueType + "(" + getFieldValueLabel() + ")";

  }
}


