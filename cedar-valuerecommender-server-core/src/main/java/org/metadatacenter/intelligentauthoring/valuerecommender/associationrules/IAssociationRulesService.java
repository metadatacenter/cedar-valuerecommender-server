package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import java.io.IOException;

public interface IAssociationRulesService
{
  void generateRulesForTemplate(String templateId) throws IOException;
}
