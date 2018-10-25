package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import weka.associations.AssociationRule;

import java.util.List;

public interface IAssociationRulesService
{
  List<EsRule> generateRulesForTemplate(String templateId) throws Exception;
  //List<AssociationRule> filterRules(List<AssociationRule> rules, List<Field> populatedFields, Field targetField);
}
