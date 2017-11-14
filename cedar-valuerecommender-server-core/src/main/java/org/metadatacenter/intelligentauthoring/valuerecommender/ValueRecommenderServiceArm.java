package org.metadatacenter.intelligentauthoring.valuerecommender;


import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;

import java.io.IOException;
import java.util.List;

public class ValueRecommenderServiceArm implements IValueRecommenderArm {

  public ValueRecommenderServiceArm(CedarConfig config) {
    // Initialize configuration manager, which will provide access to the Cedar configuration
    ConfigManager.getInstance().initialize(config);
  }

  /**
   * Generates the association mining rules for all templates in the system
   */
  @Override
  public void generateRules() {

    String templateId = "https://repo.metadatacenter.orgx/templates/ac959264-4c5b-4371-9fa1-9e22ccda5a3c";

    AssociationRulesService service = new AssociationRulesService();
    try {
      service.generateRulesForTemplate(templateId);
    } catch (IOException e) {
      e.printStackTrace();
    }


  }

  @Override
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      IOException {
//    ArmRecommender recommender = new ArmRecommender();
//    templateId = templateId.toLowerCase();
//    List<RecommendedValue> recommendedValues = null;
//    recommendedValues = recommender.getRecommendation(templateId, populatedFields, targetField);
//    Recommendation recommendation = new Recommendation(targetField.getFieldPath(), recommendedValues);
//    return recommendation;

    return null;
  }

}
