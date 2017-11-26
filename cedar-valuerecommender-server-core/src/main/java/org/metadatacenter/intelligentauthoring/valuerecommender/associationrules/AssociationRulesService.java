package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import weka.associations.Apriori;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.APRIORI_NUM_RULES;

public class AssociationRulesService implements IAssociationRulesService {

  public void generateRulesForTemplate(String templateId) throws Exception {

    // 1. Generate ARFF file for template
    String instancesFileName = AssociationRulesUtils.generateInstancesFile(templateId);

    // 2. Read the ARFF file
    DataSource source = new DataSource(instancesFileName);
    Instances data = source.getDataSet();

    // 3. Data preprocessing (apply the StringToNominal filter)
    Instances filteredData = AssociationRulesUtils.applyStringToNominalFilter(data);

    // 4. Run the Apriori algorithm
    Apriori results = AssociationRulesUtils.runApriori(filteredData, APRIORI_NUM_RULES);
    System.out.println("A Priori Rules: " + results.toString());

  }

}
