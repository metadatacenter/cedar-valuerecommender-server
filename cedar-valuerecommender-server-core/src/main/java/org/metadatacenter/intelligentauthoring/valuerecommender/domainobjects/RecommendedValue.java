package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

public class RecommendedValue implements Comparable<RecommendedValue> {

  public enum RecommendationType {CONTEXT_INDEPENDENT, CONTEXT_DEPENDENT};
  private final String valueLabel;
  private final String valueType;
  private final Double recommendationScore;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final RecommendationDetails details;

  public RecommendedValue(String valueLabel, String valueType, Double recommendationScore,
                          RecommendationDetails details) {
    this.valueLabel = valueLabel;
    this.valueType = valueType;
    this.recommendationScore = recommendationScore;
    this.details = details;
  }

  public RecommendedValue(String valueLabel, String valueType, Double recommendationScore) {
    this(valueLabel, valueType, recommendationScore, null);
  }

  public String getValueLabel() {
    return valueLabel;
  }

  public String getValueType() {
    return valueType;
  }

  public Double getRecommendationScore() {
    return recommendationScore;
  }

  public RecommendationDetails getDetails() {
    return details;
  }

  /**
   * Define equality between values to remove duplicates from the recommendations
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RecommendedValue value = (RecommendedValue) o;
    return Objects.equals(getValueLabel(), value.getValueLabel());
  }

  @Override
  public int compareTo(RecommendedValue value) {
    Double value1 = value.getRecommendationScore();
    Double value2 = getRecommendationScore();
    if (Math.abs(value1 - value2) >= 0.001) {
      return Double.compare(value1, value2);
    } else { // If the recommendation scores are close enough, return the value with higher support
      return Double.compare(value.getDetails().ruleSupport(), getDetails().ruleSupport());
    }
  }

}
