package org.metadatacenter.intelligentauthoring.valuerecommender.util;

/**
 * Constants of general utility.
 * <p>
 * All member of this class are immutable.
 */
public class Constants {

  public static final String CLUSTER_NAME = "elasticsearch_cedar";
  public static final String ES_INDEX_NAME = "cedar";
  public static final String ES_TYPE_NAME = "template_instances";
  public static final String ES_HOST = "localhost";
  public static final int ES_TRANSPORT_PORT = 9300;

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
