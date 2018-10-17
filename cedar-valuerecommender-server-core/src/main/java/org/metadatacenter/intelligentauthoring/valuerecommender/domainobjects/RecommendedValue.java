package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendedValue {
  public enum RecommendationType {CONTEXT_INDEPENDENT, CONTEXT_DEPENDENT};
  private String value;
  private String valueUri;
  private Double score;
  private Double confidence;
  private Double support;
  private RecommendationType type;

  public RecommendedValue(String value, String valueUri, Double score, Double confidence, Double support, RecommendationType type) {
    this.value = value;
    this.valueUri = valueUri;
    this.score = score;
    this.confidence = confidence;
    this.support = support;
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public String getValueUri() {
    return valueUri;
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
        "value='" + value + '\'' +
        ", valueUri='" + valueUri + '\'' +
        ", score=" + score +
        ", confidence=" + confidence +
        ", support=" + support +
        ", type=" + type +
        '}';
  }

}
