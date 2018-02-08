package org.metadatacenter.intelligentauthoring.valuerecommender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.AssociationRulesService;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRules;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ValueRecommenderServiceArm implements IValueRecommenderArm {

  protected final Logger logger = LoggerFactory.getLogger(ValueRecommenderServiceArm.class);

  public ValueRecommenderServiceArm(CedarConfig config) {
    // Initialize configuration manager, which will provide access to the Cedar configuration
    ConfigManager.getInstance().initialize(config);
  }

  /**
   * Generates association mining rules for the templates specified
   */
  @Override
  public EsRules generateRules(List<String> templateIds) {
    AssociationRulesService service = new AssociationRulesService();
    EsRules esRules = null;
    try {
      for (String templateId : templateIds) {
        logger.info("Generating rules for template id: " + templateId);
        long startTime = System.currentTimeMillis();
        esRules = service.generateRulesForTemplate(templateId);

        // Store the rules in Elasticsearch
        ObjectMapper mapper = new ObjectMapper();
        String rulesJsonString = mapper.writeValueAsString(esRules);
//        System.out.println(rulesJsonString);
//        JsonNode rulesJson = mapper.readTree(rulesJsonString);


        long endTime   = System.currentTimeMillis();
        long totalTime = (endTime - startTime)/1000;
        logger.info("Rules generation completed. Execution time: " + totalTime + " seg.");
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ProcessingException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return esRules;
  }

  @Override
  public Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws IOException {

//    final List<AssociationRule> generatedRules = generateRules();
//
//    AssociationRulesService service = new AssociationRulesService();
//
//
//    // TODO: read from body, and don't forget to lowercase!!
//    populatedFields = new ArrayList<>();
//    populatedFields.add(new Field("optionalattribute.name", "disease"));
//    populatedFields.add(new Field("tissue", "liver"));
//    //  populatedFields.add(new Field("tissue", "palpebral conjunctiva"));
//    targetField = new Field("optionalAttribute.value", null);
//
//    List<AssociationRule> rulesFound = service.filterRules(generatedRules, populatedFields, targetField);
//    System.out.println("RULES FOUND: ");
//    for (AssociationRule rule : rulesFound) {
//      System.out.println(rule);
//    }

    // TODO: create recommendations using the association rules generated

    return null;
  }

  

}
