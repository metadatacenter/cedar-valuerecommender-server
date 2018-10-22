package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

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
  public static final boolean USE_ALL_FIELDS = true; // If false, only fields with "valueRecommendationEnabled=true" are considered
  public static final String ARFF_FOLDER_NAME = "cedar-valuerecommender-server/arff-files";
  public static final String MAPPINGS_FILE_PATH = "mappings/mappings_merged.json";

  // If true, the instances are read from CEDAR. If false, they are read from a local folder
  public static final boolean READ_INSTANCES_FROM_CEDAR = true;
  public static final String CEDAR_INSTANCES_PATH =
      //"/Users/marcosmr/tmp/ARM_resources/EVALUATION/cedar_instances_annotated/ebi_cedar_instances/training";
      //"/Users/marcosmr/tmp/ARM_resources/EVALUATION/cedar_instances_annotated/ebi_cedar_instances_different_ontologies/training";
      "/Users/marcosmr/tmp/ARM_resources/EVALUATION/cedar_instances_annotated/ncbi_cedar_instances/training";

  // Apriori configuration
  public static final int APRIORI_MAX_NUM_RULES = 1000000;
  //public static double MIN_SUPPORT = 0.00004;
  public static int MIN_SUPPORTING_INSTANCES = 1/*5*/; // The support will be dynamically calculated based on this value
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

  // Field names used in the rules index
  public static final String INDEX_TEMPLATE_ID = "templateId";
  public static final String INDEX_RULE_CONFIDENCE = "confidence";
  public static final String INDEX_RULE_SUPPORT = "support";
  public static final String INDEX_PREMISE_SIZE = "premiseSize";
  public static final String INDEX_CONSEQUENCE_SIZE = "consequenceSize";
  public static final String INDEX_RULE_PREMISE = "premise";
  public static final String INDEX_RULE_CONSEQUENCE = "consequence";
  public static final String INDEX_FIELD_NORMALIZED_PATH = "fieldNormalizedPath";
  public static final String INDEX_FIELD_TYPE_MAPPINGS = "fieldTypeMappings";
  public static final String INDEX_FIELD_NORMALIZED_VALUE = "fieldNormalizedValue";
  public static final String INDEX_FIELD_VALUE_MAPPINGS = "fieldValueMappings";
  public static final String INDEX_FIELD_VALUE_RESULT = "fieldValueResult";
  public static final String INDEX_PREMISE_NORMALIZED_PATH = INDEX_RULE_PREMISE + "." + INDEX_FIELD_NORMALIZED_PATH;
  public static final String INDEX_PREMISE_TYPE_MAPPINGS = INDEX_RULE_PREMISE + "." + INDEX_FIELD_TYPE_MAPPINGS;
  public static final String INDEX_PREMISE_NORMALIZED_VALUE = INDEX_RULE_PREMISE + "." + INDEX_FIELD_NORMALIZED_VALUE;
  public static final String INDEX_PREMISE_VALUE_MAPPINGS = INDEX_RULE_PREMISE + "." + INDEX_FIELD_VALUE_MAPPINGS;
  public static final String INDEX_CONSEQUENCE_NORMALIZED_PATH = INDEX_RULE_CONSEQUENCE + "." + INDEX_FIELD_NORMALIZED_PATH;
  public static final String INDEX_CONSEQUENCE_TYPE_MAPPINGS = INDEX_RULE_CONSEQUENCE + "." + INDEX_FIELD_TYPE_MAPPINGS;
  public static final String INDEX_CONSEQUENCE_VALUE_RESULT = INDEX_RULE_CONSEQUENCE + "." + INDEX_FIELD_VALUE_RESULT;

  // Elasticsearch aggregation names and attributes used
  public static final String AGG_BY_NESTED_OBJECT = "by_nested_object";
  public static final String AGG_BY_TARGET_FIELD_VALUE_RESULT = "by_target_field_value_result";
  public static final String AGG_METRICS_INFO = "metrics_info";
  public static final String AGG_SEPARATOR = ">";
  public static final String METRIC_MAX_SCORE = "max_score";
  public static final String METRIC_SUPPORT = "max_support";
  public static final String METRIC_MAX_CONFIDENCE = "max_confidence";
  public static final String METRIC_MAX_SCORE_PATH = AGG_METRICS_INFO + AGG_SEPARATOR + METRIC_MAX_SCORE;
  public static final String METRIC_MAX_CONFIDENCE_PATH = AGG_METRICS_INFO + AGG_SEPARATOR + METRIC_MAX_CONFIDENCE;
  public static final String METRIC_SUPPORT_PATH = AGG_METRICS_INFO + AGG_SEPARATOR + METRIC_SUPPORT;


  // Value Recommender configuration
  public static boolean USE_MAPPINGS = false; // For ontology terms, match using mappings too
  public static boolean FILTER_BY_CONFIDENCE = false;
  public static final double MIN_CONFIDENCE_QUERY = 0.9; // Confidence threshold used when querying the rules
  public static boolean FILTER_BY_SUPPORT = false;
  public static final double MIN_SUPPORT_QUERY = 1; // Support threshold used when querying the rules
  public static final int MAX_RESULTS = 10;
  public static final int CONSEQUENCE_SIZE = 1;

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
