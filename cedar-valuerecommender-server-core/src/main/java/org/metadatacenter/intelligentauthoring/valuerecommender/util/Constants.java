package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  public static final String SUMMARIZED_CONTENT_FIELD = "resourceSummarizedContent";
  public static final String FIELD_SUFFIX = "_field";
  public static final String VALUE_FIELD_NAME = "@value";
  public static final String ID_FIELD_NAME = "@id";
  public static final String TYPE_FIELD_NAME = "@type";
  public static final String ITEMS_FIELD_NAME = "items";
  public static final String ARFF_MISSING_VALUE = "?";

  // Apriori configuration
  public static final int APRIORI_NUM_RULES = 5000;

  // PRIVATE //

  /**
   * The caller references the constants using Constants.EMPTY_STRING,
   * and so on. Thus, the caller should be prevented from constructing objects of
   * this class, by declaring this private constructor.
   */
  private Constants() {
    // This restricts instantiation
    throw new AssertionError();
  }
}
