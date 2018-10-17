package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.metadatacenter.config.CedarConfig;

public class ConfigManager {

  private static ConfigManager singleInstance;
  private static CedarConfig cedarConfig;

  private ConfigManager() {};

  public static ConfigManager getInstance() {
    if (singleInstance == null) {
      singleInstance = new ConfigManager();
    }
    return singleInstance;
  }

  public static void initialize(CedarConfig config) {
    cedarConfig = config;
  }

  public static CedarConfig getCedarConfig() {
    return cedarConfig;
  }

}
