package org.metadatacenter.intelligentauthoring.valuerecommender;


import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import weka.associations.AssociationRule;
import weka.associations.AssociationRules;

import java.io.IOException;
import java.util.ArrayList;
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
  public List<AssociationRule> generateRules() {

    //String templateId = "https://repo.metadatacenter.orgx/templates/9144ee94-1607-4adc-b201-3fed97abf804";
    //String templateId = "https://repo.metadatacenter.orgx/templates/ac959264-4c5b-4371-9fa1-9e22ccda5a3c"; // Flat template
    //String templateId = "https://repo.metadatacenter.orgx/templates/ba5a8bd9-c817-4e4a-b93a-6eb8e20618ea"; // Template with 3 levels of nesting
    //String templateId = "https://repo.metadatacenter.orgx/templates/daa3aa49-dca3-48b9-b4d9-ec71361ababa"; // Template with a multi-instance element
    //String templateId = "https://repo.metadatacenter.orgx/templates/a8bf8e1f-0f18-44af-a49a-6966e883a0bc"; // Template with 2 multi-instance elements
    //String templateId = "https://repo.metadatacenter.orgx/templates/ec919efc-7f3d-47d4-975a-0e9e68249e61"; // Template with an array nested inside an element

    //String templateId = "https://repo.metadatacenter.orgx/templates/314cbfaf-a4f3-4ebd-8ee4-e6f8063a648a"; // BioSample template with a couple of instances
    String templateId = "https://repo.metadatacenter.orgx/templates/f65616a4-51ff-43c4-8005-32e6673a522c"; // BioSample template with 40K instances

    AssociationRulesService service = new AssociationRulesService();
    List<AssociationRule> rules = null;
    try {
      long startTime = System.currentTimeMillis();
      // TODO: generate rules for ALL templates
      rules = service.generateRulesForTemplate(templateId);
      long endTime   = System.currentTimeMillis();
      long totalTime = (endTime - startTime)/1000;
      System.out.println("RULES GENERATION - EXECUTION TIME: " + totalTime + " seg.");
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ProcessingException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return rules;
  }

  @Override
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws IOException {

    final List<AssociationRule> generatedRules = generateRules();

    AssociationRulesService service = new AssociationRulesService();


    // TODO: read from body, and don't forget to lowercase!!
    populatedFields = new ArrayList<>();
    populatedFields.add(new Field("optionalattribute.name", "disease"));
//  populatedFields.add(new Field("optionalAttribute.value", "trachoma"));
//  populatedFields.add(new Field("tissue", "palpebral conjunctiva"));
    targetField = new Field("optionalAttribute.value", null);

    List<AssociationRule> rulesFound = service.filterRules(generatedRules, populatedFields, targetField);
    System.out.println("RULES FOUND: ");
    System.out.println(rulesFound.toString());

    // TODO: create recommendations using the association rules generated
    return null;
  }

  

}
