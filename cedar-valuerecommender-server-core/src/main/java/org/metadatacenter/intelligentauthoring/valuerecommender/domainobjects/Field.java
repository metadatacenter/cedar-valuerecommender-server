package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Template field
 */
public class Field {
  @JsonProperty("path")
  private String fieldPath;
  @JsonProperty("value")
  private String fieldValue;

  // Required for deserialization
  public Field() {}

  public Field(String fieldPath, String fieldValue) {
    this.fieldPath = fieldPath;
    this.fieldValue = fieldValue;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getFieldValue() {
    return fieldValue;
  }


}
