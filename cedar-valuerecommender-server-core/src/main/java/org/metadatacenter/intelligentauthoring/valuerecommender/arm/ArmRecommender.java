package org.metadatacenter.intelligentauthoring.valuerecommender.arm;

import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.RecommendedValue;

import java.io.IOException;
import java.util.List;

import weka.core.converters.ConverterUtils.DataSource;

/**
 * Value Recommender using Association Rule Mining
 */
public class ArmRecommender {

  public List<RecommendedValue> getRecommendation(String templateId, List<Field> populatedFields, Field targetField)
      throws IOException {
    System.out.println("I am running this thing");



    return null;
  }

}
