package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Template field
 */
public class Field {
  private String fieldId;
  @JsonProperty("path")
  private String fieldPath;
  @JsonProperty("value")
  private String fieldValue;

  // Required for deserialization
  public Field() {}

  public Field(String fieldId, String fieldPath, String fieldValue) {
    this.fieldId = fieldId;
    this.fieldPath = fieldPath;
    this.fieldValue = fieldValue;
  }

  public String getFieldId() { return fieldId; }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldValue() {
    return fieldValue;
  }


}
