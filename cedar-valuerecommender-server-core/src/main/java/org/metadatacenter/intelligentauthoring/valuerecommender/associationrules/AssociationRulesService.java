package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import weka.associations.Apriori;
import weka.associations.AssociationRule;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.*;

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
    //System.out.println("Apriori results: " + aprioriResults.toString());
    System.out.println("\nAPRIORI RESULTS: ");
    // See info about options at: http://grepcode.com/file/repo1.maven.org/maven2/nz.ac.waikato.cms.weka/weka-stable/3.6.8/weka/associations/Apriori.java
    System.out.println("Current options: " + Arrays.asList(aprioriResults.getOptions()).toString());
    System.out.println("Number of rules generated: " + aprioriResults.getNumRules());

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
      if (AssociationRulesUtils.ruleMatchesRequirements(rule, fieldValues, targetField)) {
        rulesFound.add(rule);
      }

    }

    return rulesFound;

  }

}
