package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.associations.Apriori;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.APRIORI_MAX_NUM_RULES;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.ARFF_FOLDER_NAME;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.VERBOSE_MODE;

public class AssociationRulesService implements IAssociationRulesService {

  protected final Logger logger = LoggerFactory.getLogger(AssociationRulesService.class);

  @Override
  public List<EsRule> generateRulesForTemplate(String templateId) throws Exception {

    // 1. Generate ARFF file for template
    Optional<String> instancesFileName = AssociationRulesUtils.generateInstancesFile(templateId);

    if (instancesFileName.isPresent()) {
      // 2. Read the ARFF file
      String filePath = System.getProperty("java.io.tmpdir") + "/" + ARFF_FOLDER_NAME + "/" + instancesFileName.get();
      logger.info("Reading ARFF file: " + filePath);
      DataSource source = new DataSource(filePath);
      Instances data = source.getDataSet();

      // 3. Data preprocessing (apply the StringToNominal filter)
      Instances filteredData = AssociationRulesUtils.applyStringToNominalFilter(data);
      logger.info("Applying filter: StringToNominal");

      // 4. Run the Apriori algorithm
      logger.info("Running Apriori...");
      long startTime = System.currentTimeMillis();
      Apriori aprioriResults = AssociationRulesUtils.runApriori(filteredData, APRIORI_MAX_NUM_RULES, VERBOSE_MODE);
      long aprioriTime = System.currentTimeMillis() - startTime;
      // See info about options at: http://grepcode.com/file/repo1.maven.org/maven2/nz.ac.waikato.cms
      // .weka/weka-stable/3.6.8/weka/associations/Apriori.java
      logger.info("Current options: " + Arrays.asList(aprioriResults.getOptions()).toString());
      logger.info("Numer of rules limit: " + aprioriResults.getNumRules());
      logger.info("Number of rules generated: " + aprioriResults.getAssociationRules().getRules().size());
      logger.info("Apriori execution time: " + aprioriTime + " ms");
//      logger.info("Rules:\n");
//      int ruleCount = 1;
//      for (AssociationRule rule : aprioriResults.getAssociationRules().getRules()) {
//        logger.info(ruleCount++ + ") " + rule.toString());
//      }
      return AssociationRulesUtils.toEsRules(aprioriResults, templateId);
    }
    else {
      return new ArrayList<>();
    }
  }

//  @Override
//  public List<AssociationRule> filterRules(List<AssociationRule> rules, List<Field> populatedFields, Field
//      targetField) {
//
//    Map<String, String> fieldValues = new HashMap<>();
//    for (Field populatedField : populatedFields) {
//      fieldValues.put(populatedField.getFieldPath(), populatedField.getFieldValue());
//    }
//
//    List<AssociationRule> rulesFound = new ArrayList<>();
//
//    for (AssociationRule rule : rules) {
//      if (AssociationRulesUtils.ruleMatchesRequirements(rule, fieldValues, targetField)) {
//        rulesFound.add(rule);
//      }
//
//    }
//
//    return rulesFound;
//
//  }

}
