package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

import java.util.Optional;

/**
 * This class represents an attribute value.
 */
public class ArffAttributeValue {

  private String value;
  private Optional<String> label; // used when the value is an ontology term uri

  public ArffAttributeValue(String value, Optional<String> label) {
    this.value = value;
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public Optional<String> getLabel() {
    return label;
  }

  public String getArffValueString() {
    String valueString = null;
    if (label.isPresent()) { // It is an ontology term
      valueString = "[" + value + "](" + label.get() + ")";
    } else { // Not an ontology term
      valueString = "[](" + value + ")";
    }
    return "'" + valueString.replace("'", "\\'").trim() + "'";
  }
}
