package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  //public static final String SUMMARIZED_CONTENT_FIELD = "resourceSummarizedContent";
  public static final String BIOPORTAL_API_BASE = "http://data.bioontology.org/";
  public static final String BIOPORTAL_API_CLASSES_FRAGMENT = "/classes/";
  public static final String VALUE_FIELD_NAME = "@value";
  public static final String ID_FIELD_NAME = "@id";
  public static final String LABEL_FIELD_NAME = "rdfs:label";
  public static final String TYPE_FIELD_NAME = "@type";
  public static final String ITEMS_FIELD_NAME = "items";
  public static final String UI_FIELD_NAME = "_ui";
  public static final String PROPERTIES_FIELD_NAME = "properties";
  public static final String ONEOF_FIELD_NAME = "oneOf";
  public static final String ENUM_FIELD_NAME = "enum";
  public static final String RECOMMENDATION_ENABLED_FIELD_NAME = "valueRecommendationEnabled";
  public static final String ARFF_MISSING_VALUE = "?";
  public static final int MAX_INSTANCES_FOR_ARM = -1; // -1 means that there is no limit
  public static final boolean USE_ALL_FIELDS = false; // If true, only fields with "valueRecommendationEnabled=true" are considered
  public static final String ARFF_FOLDER_NAME = "cedar-valuerecommender-server/arff-files";

  // If true, the instances are read from CEDAR. If false, they are read from a local folder
  public static final boolean READ_INSTANCES_FROM_CEDAR = false;
  public static final String CEDAR_INSTANCES_PATH =
      //"/Users/marcosmr/Desktop/tmp/non_annotated_instances_big";
      //"/Users/marcosmr/Desktop/tmp/annotated_instances_big";
      //"/Users/marcosmr/tmp/ARM_resources/evaluation_results/2018_03_26_1-training_124200_ebi-testing-12800_ebi/training_samples_annotated";
      "/Users/marcosmr/tmp/ARM_resources/evaluation_results/2018_03_25_1-training_124200_ncbi-testing-12800_ncbi/training_samples_annotated";

  // Apriori configuration
  public static final int APRIORI_MAX_NUM_RULES = 1000000;
  //public static double MIN_SUPPORT = 0.00004;
  public static int MIN_SUPPORTING_INSTANCES = 5; // The support will be dynamically calculated based on this value
  public static final double MIN_CONFIDENCE = 0.3;
  public static final double MIN_LIFT = 1.2;
  public static final double MIN_LEVERAGE = 1.1;
  public static final double MIN_CONVICTION = 1.1;
  public static final int METRIC_TYPE_ID = 0; // 0 = Confidence | 1 = Lift | 2 = Leverage | 3 = Conviction
  public static final String SUPPORT_METRIC_NAME = "Support";
  public static final String CONFIDENCE_METRIC_NAME = "Confidence";
  public static final String LIFT_METRIC_NAME = "Lift";
  public static final String LEVERAGE_METRIC_NAME = "Leverage";
  public static final String CONVICTION_METRIC_NAME = "Conviction";
  public static final boolean VERBOSE_MODE = true;

  // Value Recommender configuration
  public static boolean FILTER_BY_CONFIDENCE = false;
  public static final double MIN_CONFIDENCE_QUERY = 0.9; // Confidence threshold used when querying the rules
  public static boolean FILTER_BY_SUPPORT = false;
  public static final double MIN_SUPPORT_QUERY = 20; // Confidence threshold used when querying the rules
  public static final int MAX_RESULTS = 10;

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
