package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendedValue {
  public enum RecommendationType {CONTEXT_INDEPENDENT, CONTEXT_DEPENDENT};
  private final String valueLabel;
  private final String valueId;
  private final Double score;
  private final Double confidence;
  private final Double support;
  private final RecommendationType type;

  public RecommendedValue(String valueLabel, String valueId, Double score, Double confidence, Double support, RecommendationType type) {
    this.valueLabel = valueLabel;
    this.valueId = valueId;
    this.score = score;
    this.confidence = confidence;
    this.support = support;
    this.type = type;
  }

  public String getValueLabel() {
    return valueLabel;
  }

  public String getValueId() {
    return valueId;
  }

  public Double getScore() {
    return score;
  }

  public Double getConfidence() { return confidence; }

  public Double getSupport() { return support; }

  public RecommendationType getType() {
    return type;
  }

  @Override
  public String toString() {
    return "RecommendedValue{" +
        "valueLabel='" + valueLabel + '\'' +
        ", valueId='" + valueId + '\'' +
        ", score=" + score +
        ", confidence=" + confidence +
        ", support=" + support +
        ", type=" + type +
        '}';
  }
}
