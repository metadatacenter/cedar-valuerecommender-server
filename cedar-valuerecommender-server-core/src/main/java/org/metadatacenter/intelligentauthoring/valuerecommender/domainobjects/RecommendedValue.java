package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public class RecommendedValue
{
  private String value;
  private double score;

  public String getValue()
  {
    return value;
  }

  public void setValue(String value)
  {
    this.value = value;
  }

  public double getScore()
  {
    return score;
  }

  public void setScore(double score)
  {
    this.score = score;
  }

  public RecommendedValue(String value, double score)
  {

    this.value = value;
    this.score = score;
  }

  @Override
  public String toString() {
    return "RecommendedValue{" +
        "value='" + value + '\'' +
        ", score=" + score +
        '}';
  }
}
