package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

/**
 * A component of the premise/consequence of an association rule
 */
public class EsRuleItem {

  private String fieldPath;
  private String fieldInstanceType;
  private String fieldNormalizedPath;
  private String fieldValue;
  private String fieldValueLabel;
  private String fieldNormalizedValue;

  /**
   * TODO: We are currently using two different fields: fieldPath and fieldInstanceType to identify the field in the
   * template. Think if it would be a good idea to have just one field to store the path, either for free text (e.g.,
   * study.title), for ontology terms (e.g., uri1.uri2) or mixed (e.g., uri1.title).
   * - Current limitation: for ontology terms we are storing the current field uri using the fieldInstanceType field,
   * but we are not storing the path to the field. We could use a new field, such as "fieldNormalizedPath" to identify
   * the field in the template. That field would store the path, either using free text, ontology terms, or both.
   */

  /**
   * @param fieldPath
   * @param fieldInstanceType    Instance type. It is the content of the @type field, if it exists.
   * @param fieldNormalizedPath  Normalized path. For fields without an instance type, a normalized string (e.g.,
   *                             STUDY.TITLE). For fields that have been annotated with an instance type, it contains
   *                             the term URI that annotates the field (e.g., http://purl.obolibrary.org/obo/DOID_4)
   * @param fieldValue           Content of the @value or the @id field
   * @param fieldValueLabel      When the value is a term uri (from @id), it stores the corresponding label (rdfs:label)
   * @param fieldNormalizedValue Normalized value. For free text values, a normalized string. For ontology terms, it
   *                             contains the term URI.
   *                             <p>
   *                             Examples:
   *                             1) For: {"@value": "colorectal cancer"}
   *                             - fieldInstanceType = null
   *                             - fieldValue = colorectal cancer
   *                             - fieldNormalizedValue = COLORECTALCANCER
   *                             2) For {"@id": "http://purl.obolibrary.org/obo/DOID_9256",
   *                             "rdfs:label": "colorectal cancer",
   *                             "@type":"http://purl.obolibrary.org/obo/DOID_4"}
   *                             - fieldInstanceType = http://purl.obolibrary.org/obo/DOID_4 ("disease" in DOID)
   *                             - fieldValue = colorectal cancer
   *                             - fieldNormalizedValue = http://purl.obolibrary.org/obo/DOID_9256
   */
  public EsRuleItem(String fieldPath, String fieldInstanceType, String fieldNormalizedPath, String fieldValue,
                    String fieldValueLabel, String fieldNormalizedValue) {
    this.fieldPath = fieldPath;
    this.fieldInstanceType = fieldInstanceType;
    this.fieldNormalizedPath = fieldNormalizedPath;
    this.fieldValue = fieldValue;
    this.fieldValueLabel = fieldValueLabel;
    this.fieldNormalizedValue = fieldNormalizedValue;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldInstanceType() {
    return fieldInstanceType;
  }

  public String getFieldNormalizedPath() {
    return fieldNormalizedPath;
  }

  public String getFieldValue() {
    return fieldValue;
  }

  public String getFieldValueLabel() {
    return fieldValueLabel;
  }

  public String getFieldNormalizedValue() {
    return fieldNormalizedValue;
  }

  public String toPrettyString() {
    String stringField = "[" + getFieldInstanceType() + "]" + "(" + getFieldPath() + ")";
    return stringField + "=" + "[" + getFieldValue() + "]" + "(" + getFieldValueLabel() + ")";
  }
}


