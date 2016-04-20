package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * <p>
 * All member of this class are immutable.
 */
public class Constants {

  public static final String CEDAR_HOME = "CEDAR_HOME";
  public static final String CONF_PATH = "cedar-valuerecommender-server/cedar-valuerecommender-server-core/src/main/config/config.properties";

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
