package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * This class represents an attribute value.
 */
public class FieldValueResultUtils {

  public static String toValueResultString(String fieldValueType, String fieldValueLabel) {
    String valueString = null;
    if (fieldValueType != null) { // ontology term
      valueString = "[" + fieldValueType + "](" + fieldValueLabel + ")";
    } else { // free text
      valueString = "[](" + fieldValueLabel + ")";
    }
    return valueString.replace("'", "\\'").trim();
  }
}
