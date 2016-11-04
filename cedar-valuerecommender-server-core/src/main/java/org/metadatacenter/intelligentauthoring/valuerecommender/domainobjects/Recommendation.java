package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

import java.util.List;

/**
 * A recommendation for a field's value
 */
public class Recommendation
{
  private String fieldPath;
  private List<RecommendedValue> suggestedValues;

  public Recommendation(String fieldPath, List<RecommendedValue> suggestedValues)
  {
    this.fieldPath = fieldPath;
    this.suggestedValues = suggestedValues;
  }

  public String getFieldPath()
  {
    return fieldPath;
  }

  public void setFieldName(String fieldPath)
  {
    this.fieldPath = fieldPath;
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
