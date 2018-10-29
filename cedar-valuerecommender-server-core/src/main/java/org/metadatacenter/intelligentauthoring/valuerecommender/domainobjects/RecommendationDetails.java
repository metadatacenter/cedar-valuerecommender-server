package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendationDetails {

  private final String rule;
  private final Double contextMatchingScore;
  private final Double ruleConfidence;
  private final Double ruleSupport;
  private final RecommendedValue.RecommendationType recommendationType;

  public RecommendationDetails(String rule, Double contextMatchingScore, Double ruleConfidence, Double ruleSupport,
                               RecommendedValue.RecommendationType recommendationType) {

    this.rule = rule;
    this.contextMatchingScore = contextMatchingScore;
    this.ruleConfidence = ruleConfidence;
    this.ruleSupport = ruleSupport;
    this.recommendationType = recommendationType;
  }

  public String getRule() {
    return rule;
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
