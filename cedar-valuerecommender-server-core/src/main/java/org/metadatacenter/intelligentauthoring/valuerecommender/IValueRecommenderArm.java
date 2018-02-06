package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRules;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import weka.associations.AssociationRule;

import java.io.IOException;
import java.util.List;

/**
 * Value Recommender service interface using Association Rule Mining (ARM)
 */
public interface IValueRecommenderArm {

  EsRules generateRules(List<String> templateIds);
  Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws IOException;

}
