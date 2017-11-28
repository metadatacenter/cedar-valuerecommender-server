package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import weka.associations.Apriori;
import weka.associations.AssociationRule;
import weka.associations.Item;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.APRIORI_NUM_RULES;

public class AssociationRulesService implements IAssociationRulesService {

  @Override
  public List<AssociationRule> generateRulesForTemplate(String templateId) throws Exception {

    // 1. Generate ARFF file for template
    String instancesFileName = AssociationRulesUtils.generateInstancesFile(templateId);

    // 2. Read the ARFF file
    DataSource source = new DataSource(instancesFileName);
    Instances data = source.getDataSet();

    // 3. Data preprocessing (apply the StringToNominal filter)
    Instances filteredData = AssociationRulesUtils.applyStringToNominalFilter(data);

    // 4. Run the Apriori algorithm
    Apriori aprioriResults = AssociationRulesUtils.runApriori(filteredData, APRIORI_NUM_RULES);
    System.out.println("A Priori Rules: " + aprioriResults.toString());

    return aprioriResults.getAssociationRules().getRules();
  }

  @Override
  public List<AssociationRule> filterRules(List<AssociationRule> rules, List<Field> populatedFields, Field targetField) {

    Map<String, String> fieldValues = new HashMap<>();
    for (Field populatedField : populatedFields) {
      fieldValues.put(populatedField.getFieldPath(), populatedField.getFieldValue());
    }

    List<AssociationRule> rulesFound = new ArrayList<>();

    for (AssociationRule rule : rules) {
      System.out.println("Checking rule: " + rule);
      if (ruleMatchesRequirements(rule, fieldValues, targetField)) {
        System.out.println("MATCH!!");
        rulesFound.add(rule);
      }

    }

    return rulesFound;

  }

  // TODO: move to utils
  private boolean ruleMatchesRequirements(AssociationRule rule, Map<String, String> fieldValues, Field targetField) {

    // Check consequence (in general, faster that checking the premise). Note that we only require that the target
    // Field is part of the consequence. Other fields in the consequence are ignored
    boolean targetFieldFound = false;
    if (rule.getConsequence().size() > 0) {
      List<Item> consequenceItems =  new ArrayList(rule.getConsequence());
      for (Item consequenceItem : consequenceItems) {
        String attributeName = consequenceItem.getAttribute().name().toLowerCase();
        if (targetField.getFieldPath().toLowerCase().equals(attributeName)) {
          targetFieldFound = true;
        }
      }
      if (!targetFieldFound) {
        return false;
      }
    }
    else {
      return false;
    }

    // Check premise
    if (rule.getPremise().size() == fieldValues.size()) {
      List<Item> premiseItems =  new ArrayList(rule.getPremise());
      for (Item premiseItem : premiseItems) {
        String attributeName = premiseItem.getAttribute().name().toLowerCase();
        String attributeValue = premiseItem.getItemValueAsString().toLowerCase();
        if (!fieldValues.containsKey(attributeName) || !fieldValues.get(attributeName).toLowerCase().equals(attributeValue)) {
          return false;
        }
      }
    }
    else {
      return false;
    }
    return true;
  }

}
