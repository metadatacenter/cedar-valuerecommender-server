package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import java.text.Normalizer;

public class TextUtils {

  /**
   * Basic string normalization
   * @param value
   * @return
   */
  public static String normalize(String value) {
    value = value.trim();
    value = Normalizer.normalize(value, Normalizer.Form.NFD);
    // Removes all (Unicode) characters that are neither letters nor (decimal) digits
    value = value.replaceAll("[^\\p{L}\\p{Nd}]+", "");

    // Drop all non-ASCII characters
    //value = value.replaceAll("[^\\p{ASCII}]", "");
    // Replace whitespace with underscore
    //value = value.replaceAll("(\\s|-)", "_");
    // Remove unsupported characters
    //value = value.replaceAll("[^A-Z0-9_]", "");
    // Uppercase
    value = value.toUpperCase();


    return value;
  }
}