package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

/**
 * A component of the premise/consequence of an association rule
 */
public class EsRuleItem {

  private String fieldId;
  private String fieldPath;
  private String fieldValue;
  private String fieldNormalizedValue;

  public EsRuleItem(String fieldId, String fieldPath, String fieldValue, String fieldNormalizedValue) {
    this.fieldId = fieldId;
    this.fieldPath = fieldPath;
    this.fieldValue = fieldValue;
    this.fieldNormalizedValue = fieldNormalizedValue;
  }

  public String getFieldId() {
    return fieldId;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldValue() {
    return fieldValue;
  }

  public String getFieldNormalizedValue() {
    return fieldNormalizedValue;
  }

  @Override
  public String toString() {
    return "EsRuleItem{" +
        "fieldId='" + fieldId + '\'' +
        ", fieldPath='" + fieldPath + '\'' +
        ", fieldValue='" + fieldValue + '\'' +
        ", fieldNormalizedValue='" + fieldNormalizedValue + '\'' +
        '}';
  }
}


