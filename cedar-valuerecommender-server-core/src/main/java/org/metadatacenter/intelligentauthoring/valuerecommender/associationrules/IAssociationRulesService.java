package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import java.io.IOException;

public interface IAssociationRulesService
{
  void generateRulesForTemplate(String templateId) throws IOException, ProcessingException;
}
