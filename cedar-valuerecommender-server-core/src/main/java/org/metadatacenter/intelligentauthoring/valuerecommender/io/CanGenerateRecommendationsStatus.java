package org.metadatacenter.intelligentauthoring.valuerecommender.io;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is used to let the client know if it is potentially possible to generate
 * recommendations for a given template
 */
public class CanGenerateRecommendationsStatus {
  private final String templateId;
  @JsonProperty // if this annotation is missing, the "canGenerateRecommendations" property is ignored
  private final boolean canGenerateRecommendations;

  public CanGenerateRecommendationsStatus(String templateId, boolean canGenerateRecommendations) {
    this.templateId = templateId;
    this.canGenerateRecommendations = canGenerateRecommendations;
  }

  public String getTemplateId() {
    return templateId;
  }

  public boolean canGenerateRecommendations() {
    return canGenerateRecommendations;
  }
}
