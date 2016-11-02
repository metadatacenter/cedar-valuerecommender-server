package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendedValue {
  private String value;
  private String valueUri;
  private double score;

  public RecommendedValue(String value, String valueUri, double score) {
    this.value = value;
    this.valueUri = valueUri;
    this.score = score;
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
}
