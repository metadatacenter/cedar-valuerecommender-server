package org.metadatacenter.cedar.valuerecommender.utils;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  public static final String APP_MODULE_FOLDER_NAME = "cedar-valuerecommender-server-application";
  public static final String INPUT_SCHEMA_PATH = "src/main/resources/validation";
  public static final String RECOMMEND_VALUES_SCHEMA_FILE = "recommendValues-schema.json";

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
