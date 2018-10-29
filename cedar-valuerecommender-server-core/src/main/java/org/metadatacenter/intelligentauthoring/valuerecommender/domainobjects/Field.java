package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Template field
 */
public class Field {
  @JsonProperty("fieldPath")
  private String fieldPath;
  @JsonProperty("fieldValueLabel")
  private String fieldValueLabel;
  @JsonProperty("fieldValueType")
  private String fieldValueType;

  // Required for deserialization
  public Field() {}

  public Field(String fieldPath, String fieldValueLabel, String fieldValueType) {
    this.fieldPath = fieldPath;
    this.fieldValueLabel = fieldValueLabel;
    this.fieldValueType = fieldValueType;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldValueLabel() {
    return fieldValueLabel;
  }

  public String getFieldValueType() {
    return fieldValueType;
  }

}
