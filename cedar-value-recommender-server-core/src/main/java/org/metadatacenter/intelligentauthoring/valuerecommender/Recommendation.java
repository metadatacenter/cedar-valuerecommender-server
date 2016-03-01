package org.metadatacenter.intelligentauthoring.valuerecommender;

import java.util.List;

/**
 * A recommendation for a field's value
 */
public class Recommendation
{
  private String fieldName;
  private List<RecommendedValue> suggestedValues;

  public Recommendation(String fieldName, List<RecommendedValue> suggestedValues)
  {
    this.fieldName = fieldName;
    this.suggestedValues = suggestedValues;
  }

  public String getFieldName()
  {
    return fieldName;
  }

  public void setFieldName(String fieldName)
  {
    this.fieldName = fieldName;
  }

  public List<RecommendedValue> getRecommendedValues()
  {
    return suggestedValues;
  }

  public void setRecommendedValues(List<RecommendedValue> suggestedValues)
  {
    this.suggestedValues = suggestedValues;
  }
}
