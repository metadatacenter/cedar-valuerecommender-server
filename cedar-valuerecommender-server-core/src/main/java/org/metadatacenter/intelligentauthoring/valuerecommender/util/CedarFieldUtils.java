package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;

public class CedarFieldUtils {

  /**
   * @param field
   * @return The field normalized value. For ontology terms, it returns the term uri. For free text values, it
   * returns the value after applying a basic normalization
   */
  public static String normalizeFieldValue(Field field) {
    if (field.getFieldValueType() != null && !field.getFieldValueType().isEmpty()) {
      return field.getFieldValueType();
    }
    else {
      return CedarTextUtils.normalizeValue(field.getFieldValueLabel());
    }
  }
}
