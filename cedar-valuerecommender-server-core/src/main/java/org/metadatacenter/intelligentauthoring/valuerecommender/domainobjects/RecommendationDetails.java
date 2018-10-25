package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendationDetails {

  private final Double contextMatchingScore;
  private final Double ruleConfidence;
  private final Double ruleSupport;
  private final RecommendedValue.RecommendationType recommendationType;

  public RecommendationDetails(Double contextMatchingScore, Double ruleConfidence, Double ruleSupport,
                               RecommendedValue.RecommendationType recommendationType) {
    this.contextMatchingScore = contextMatchingScore;
    this.ruleConfidence = ruleConfidence;
    this.ruleSupport = ruleSupport;
    this.recommendationType = recommendationType;
  }

  public Double getContextMatchingScore() {
    return contextMatchingScore;
  }

  public Double getRuleConfidence() {
    return ruleConfidence;
  }

  public Double getRuleSupport() {
    return ruleSupport;
  }

  public RecommendedValue.RecommendationType getRecommendationType() {
    return recommendationType;
  }
}
