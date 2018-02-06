package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

/**
 * A component of the premise/consequence of an association rule
 */
public class EsRuleItem {

  private String fieldId;
  private String fieldName;
  private String fieldValue;

  public EsRuleItem(String fieldId, String fieldName, String fieldValue) {
    this.fieldId = fieldId;
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public String getFieldId() {
    return fieldId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getFieldValue() {
    return fieldValue;
  }

  @Override
  public String toString() {
    return "EsRuleItem{" +
        "fieldId='" + fieldId + '\'' +
        ", fieldName='" + fieldName + '\'' +
        ", fieldValue='" + fieldValue + '\'' +
        '}';
  }

}


