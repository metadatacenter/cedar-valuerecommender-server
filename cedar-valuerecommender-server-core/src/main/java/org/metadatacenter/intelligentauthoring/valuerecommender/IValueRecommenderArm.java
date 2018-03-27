package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;

import java.io.IOException;
import java.util.List;

/**
 * Value Recommender service interface using Association Rule Mining (ARM)
 */
public interface IValueRecommenderArm {

  List<EsRule> generateRules(List<String> templateIds);
  Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField, boolean strictMatch) throws IOException;

}
