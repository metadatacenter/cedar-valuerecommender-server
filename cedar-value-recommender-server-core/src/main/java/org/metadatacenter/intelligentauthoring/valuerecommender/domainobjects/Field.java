package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Template field
 */
public class Field {
  @JsonProperty("name")
  private String fieldName;
  @JsonProperty("value")
  private String fieldValue;

  // Required for deserialization
  public Field() {

  }

  public Field(String fieldName, String fieldValue) {
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getFieldValue() {
    return fieldValue;
  }

  public void setFieldValue(String fieldValue) {
    this.fieldValue = fieldValue;
  }

  @Override
  public String toString() {
    return "Field{" +
        "fieldName='" + fieldName + '\'' +
        ", fieldValue='" + fieldValue + '\'' +
        '}';
  }
}
