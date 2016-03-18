package utils;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  public static final String PLAY_MODULE_FOLDER_NAME = "cedar-valuerecommender-server-play";
  public static final String PLAY_APP_FOLDER_NAME = "cedar-valuerecommender-server";
  public static final String INPUT_SCHEMA_PATH = "resources/validation/input-schema.json";

  /* Error messages */
  public static final String NOT_FOUND_MSG = "Resource not found";
  public static final String BAD_REQUEST_MSG = "Bad request";
  public static final String VALIDATION_ERROR_MSG = "Validation error";
  public static final String INTERNAL_ERROR_MSG = "Internal error";

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
