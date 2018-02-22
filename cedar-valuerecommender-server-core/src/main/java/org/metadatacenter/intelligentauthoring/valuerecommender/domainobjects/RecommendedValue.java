package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendedValue {
  public enum RecommendationType {CONTEXT_INDEPENDENT, CONTEXT_DEPENDENT};
  private String value;
  private String valueUri;
  private double score;
  private RecommendationType type;

  public RecommendedValue(String value, String valueUri, double score, RecommendationType type) {
    this.value = value;
    this.valueUri = valueUri;
    this.score = score;
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public String getValueUri() {
    return valueUri;
  }

  public double getScore() {
    return score;
  }

  public RecommendationType getType() {
    return type;
  }

  @Override
  public String toString() {
    return "RecommendedValue{" +
        "value='" + value + '\'' +
        ", valueUri='" + valueUri + '\'' +
        ", score=" + score +
        ", type=" + type +
        '}';
  }
}
