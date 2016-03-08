package utils;

/**
 * Constants of general utility.
 * All member of this class are immutable.
 */
public class Constants {

  public static final String PLAY_MODULE_FOLDER_NAME = "cedar-value-recommender-server-play";
  public static final String PLAY_APP_FOLDER_NAME = "cedar-value-recommender-server";
  public static final String INPUT_SCHEMA_PATH = "resources/validation/input-schema.json";

  /* Error messages */
  public static final String ERROR_NOT_FOUND = "Resource not found";
  public static final String ERROR_BAD_REQUEST = "Bad request";
  public static final String ERROR_VALIDATION = "Validation error";
  public static final String ERROR_INTERNAL = "Internal error";


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
