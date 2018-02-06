package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

import java.util.List;

/**
 * All association rules for a particular template
 */
public class EsRules {

  private String templateId;
  private List<EsRule> rules;

  public EsRules(String templateId, List<EsRule> rules) {
    this.templateId = templateId;
    this.rules = rules;
  }

  public String getTemplateId() {
    return templateId;
  }

  public List<EsRule> getRules() {
    return rules;
  }

  @Override
  public String toString() {
    return "EsRules{" +
        "templateId='" + templateId + '\'' +
        ", rules=" + rules +
        '}';
  }

}
