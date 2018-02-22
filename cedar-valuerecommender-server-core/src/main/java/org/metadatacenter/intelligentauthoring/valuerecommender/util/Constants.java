package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  public static final String SUMMARIZED_CONTENT_FIELD = "resourceSummarizedContent";
  public static final String VALUE_FIELD_NAME = "@value";
  public static final String ID_FIELD_NAME = "@id";
  public static final String LABEL_FIELD_NAME = "rdfs:label";
  public static final String TYPE_FIELD_NAME = "@type";
  public static final String ITEMS_FIELD_NAME = "items";
  public static final String UI_FIELD_NAME = "_ui";
  public static final String RECOMMENDATION_ENABLED_FIELD_NAME = "valueRecommendationEnabled";
  public static final String ARFF_MISSING_VALUE = "?";
  public static final int MAX_INSTANCES_FOR_ARM = 1000; // -1 means that there is no limit
  public static final boolean USE_ALL_FIELDS = true; // If true, only fields with "valueRecommendationEnabled=true" are considered

  // Apriori configuration
  public static final int APRIORI_NUM_RULES = 100000;
  public static final double MIN_SUPPORT = 0.001;
  public static final double MIN_CONFIDENCE = 0.5;
  public static final double MIN_LIFT = 1.1;
  public static final double MIN_LEVERAGE = 1.1;
  public static final double MIN_CONVICTION = 1.1;
  public static final int METRIC_TYPE_ID = 0; // 0 = Confidence | 1 = Lift | 2 = Leverage | 3 = Conviction
  public static final String SUPPORT_METRIC_NAME = "Support";
  public static final String CONFIDENCE_METRIC_NAME = "Confidence";
  public static final String LIFT_METRIC_NAME = "Lift";
  public static final String LEVERAGE_METRIC_NAME = "Leverage";
  public static final String CONVICTION_METRIC_NAME = "Conviction";

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
