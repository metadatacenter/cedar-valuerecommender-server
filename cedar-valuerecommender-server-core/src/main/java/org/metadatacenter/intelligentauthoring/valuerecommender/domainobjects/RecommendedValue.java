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

  public void setValue(String value) {
    this.value = value;
  }

  public String getValueUri() {
    return valueUri;
  }

  public void setValueUri(String valueUri) {
    this.valueUri = valueUri;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public RecommendationType getType() {
    return type;
  }

  public void setType(RecommendationType type) {
    this.type = type;
  }
}
