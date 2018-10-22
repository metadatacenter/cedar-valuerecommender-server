package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

import java.util.Optional;

/**
 * This class represents an attribute value.
 */
public class ArffAttributeValue {

  private final String valueType; // an ontology term URI
  private final String valueLabel; // the term label

  public ArffAttributeValue(String valueType, String valueLabel) {
    this.valueType = valueType;
    this.valueLabel = valueLabel;
  }

  public ArffAttributeValue(String valueLabel) {
    this(null, valueLabel);
  }

  public String getValueType() {
    return valueType;
  }

  public String getValueLabel() {
    return valueLabel;
  }

  public String getArffValueString() {
    String valueString = null;
    if (valueType != null) { // ontology term
      valueString = "[" + valueType + "](" + valueLabel + ")";
    } else { // free text
      valueString = "[](" + valueLabel + ")";
    }
    return "'" + valueString.replace("'", "\\'").trim() + "'";
  }
}
