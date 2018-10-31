package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.MongoClient;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.ArffAttributeValue;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRule;
import org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch.EsRuleItem;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.FieldValueResultUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.mappings.MappingsService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarTextUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.TemplateNode;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.associations.Apriori;
import weka.associations.AssociationRule;
import weka.associations.Item;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;
import java.io.*;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

/**
 * Utilities to generate and manage association rules using Weka
 */
public class AssociationRulesUtils {

  private static ElasticsearchQueryService esQueryService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private static TemplateService<String, JsonNode> templateService;
  private static final Logger logger = LoggerFactory.getLogger(AssociationRulesUtils.class);

  static {
    try {
      // Initialize ElasticsearchQueryService
      esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
      // Initialize template and template instance services
      CedarDataServices.initializeMongoClientFactoryForDocuments(ConfigManager.getCedarConfig()
          .getTemplateServerConfig().getMongoConnection());
      MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
      MongoConfig templateServerConfig = ConfigManager.getCedarConfig().getTemplateServerConfig();
      templateService = new TemplateServiceMongoDB(mongoClientForDocuments,
          templateServerConfig.getDatabaseName(),
          templateServerConfig.getMongoCollectionName(CedarNodeType.TEMPLATE));
      templateInstanceService = new TemplateInstanceServiceMongoDB(mongoClientForDocuments,
          templateServerConfig.getDatabaseName(),
          templateServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));
    } catch (UnknownHostException e) {
      logger.error(e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Generates an ARFF file with information from all the instances for a given template
   *
   * @param templateId
   * @return The name of the ARFF file that was created
   * @throws IOException
   * @throws ProcessingException
   */
  public static Optional<String> generateInstancesFile(String templateId) throws Exception {

    logger.info("Generating ARFF file");

    String fileName = templateId.substring(templateId.lastIndexOf("/") + 1) + ".arff";
    String filePath = System.getProperty("java.io.tmpdir") + "/" + ARFF_FOLDER_NAME + "/" + fileName;
    File file = new File(filePath);
    file.getParentFile().mkdirs();
    PrintWriter fileWriter = new PrintWriter(new FileWriter(file));
    logger.info("File path: " + file.getAbsolutePath());
    fileWriter.println("% ARFF file for CEDAR template id = " + templateId);
    fileWriter.println("\n@relation example\n");

    // 1. Get instance attributes
    JsonNode template = templateService.findTemplate(templateId);
    if (template == null) {
      logger.warn("Template not found (id=" + templateId + ")");
      return Optional.empty();
    }
    List<TemplateNode> nodes = CedarUtils.getTemplateNodes(template, null, null);

    // Field nodes
    List<TemplateNode> fieldNodes = new ArrayList<>();
    for (TemplateNode node : nodes) {
      if (node.getType().equals(CedarNodeType.FIELD)) {
        if (USE_ALL_FIELDS) { // Use all fields to generate rules
          fieldNodes.add(node);
        } else { // Consider only field for which valueRecommendations are enabled
          if (node.isValueRecommendationEnabled()) {
            fieldNodes.add(node);
          }
        }
      }
    }

    // If there are no fields we cannot generate a valid ARFF file
    if (fieldNodes.isEmpty()) {
      logger.info("Skipping generation of ARFF file for template id = " + templateId + " (no attributes available)");
      file.delete();
      return Optional.empty();
    }
    else {
      // Generate ARFF attributes
      for (TemplateNode node : fieldNodes) {
        if (node.getType().equals(CedarNodeType.FIELD)) {
          fileWriter.println(toWekaAttributeFormat(node, " string"));
        }
      }

      // Array nodes
      List<TemplateNode> arrayNodes = new ArrayList<>();
      for (TemplateNode node : nodes) {
        if (node.isArray()) {
          arrayNodes.add(node);
        }
      }

      // 2. Get template instances in ARFF format
      fileWriter.println("\n@data");
      final AtomicInteger instancesCount = new AtomicInteger(0);

      Map<String, Integer> arraysPathsAndIndexes = new LinkedHashMap<>();
      Integer initialIndex = 0;
      for (TemplateNode arrayNode : arrayNodes) {
        arraysPathsAndIndexes.put(arrayNode.generatePathDotNotation(), initialIndex);
      }

      if (READ_INSTANCES_FROM_CEDAR) { // Read instances from the CEDAR system
        List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);

        for (String tiId : templateInstancesIds) {
          JsonNode ti = templateInstanceService.findTemplateInstance(tiId);
          Object tiDocument = Configuration.defaultConfiguration().jsonProvider().parse(ti.toString());
          // Transform the template instances to a list of ARFF instances
          List<ArffInstance> arffInstances = generateArffInstances(tiDocument, fieldNodes, arrayNodes, new ArrayList
              (arraysPathsAndIndexes.values()), 0, null);

          for (ArffInstance arffInstance : arffInstances) {
            fileWriter.println(arffInstance.toArffFormat());
          }
          instancesCount.getAndAdd(1);
          if (MAX_INSTANCES_FOR_ARM > 0 && instancesCount.get() == MAX_INSTANCES_FOR_ARM) {
            break;
          }
        }
      } else { // Read instances from a local folder
        try (Stream<Path> paths = Files.walk(Paths.get(CEDAR_INSTANCES_PATH))) {
          paths.filter(Files::isRegularFile).forEach(p -> {
            try {
              if (MAX_INSTANCES_FOR_ARM == -1 || instancesCount.get() < MAX_INSTANCES_FOR_ARM) {

                String fileContent = new String(Files.readAllBytes(p));
                Object tiDocument = Configuration.defaultConfiguration().jsonProvider().parse(fileContent);
                // Transform the template instances to a list of ARFF instances
                List<ArffInstance> arffInstances = generateArffInstances(tiDocument, fieldNodes, arrayNodes, new ArrayList
                    (arraysPathsAndIndexes.values()), 0, null);

                for (ArffInstance arffInstance : arffInstances) {
                  fileWriter.println(arffInstance.toArffFormat());
                }

                instancesCount.getAndAdd(1);
                if (instancesCount.get() % 1000 == 0) {
                  logger.info("No. instances processed: " + instancesCount.get());
                }
              }
            } catch (FileNotFoundException e) {
              e.printStackTrace();
            } catch (IOException e) {
              e.printStackTrace();
            }
          });
        }
      }

      fileWriter.close();
      if (instancesCount.get() == 0) { // No instances have been generated
        logger.info("No data found for template (template id: " + templateId + ")");
        file.delete();
        return Optional.empty();
      }
      logger.info("ARFF file created successfully");
      return Optional.ofNullable(fileName);
    }
  }

  /**
   * Make multiple calls to generate instances using array combinations
   *
   * @param templateInstanceDocument
   * @param templateInstanceDocument
   * @param fieldNodes
   * @param arrayNodes
   * @param arraysIndexes
   * @param indexOfCurrentArray
   * @param results
   * @return
   */
  private static List<ArffInstance> generateArffInstances(Object templateInstanceDocument,
                                                          List<TemplateNode> fieldNodes, List<TemplateNode> arrayNodes,
                                                          List<Integer> arraysIndexes,
                                                          int indexOfCurrentArray, List<ArffInstance> results) {
    if (results == null) {
      results = new ArrayList<>();
    }

    // If there are no arrays, a template instance will generate exactly an ARRF instance
    if (arrayNodes.size() == 0) {
      ArffInstance arffInstance = generateArffInstance(templateInstanceDocument, fieldNodes, null, null);
      results.add(arffInstance);
    }
    // If there are arrays, a template instance will generate multiple ARFF instances. We will need to generate all
    // possible combinations between indexes of different array and make multiple calls to "generateArffInstance"
    else {
      List<String> arraysPaths = new ArrayList<>();
      for (TemplateNode arrayNode : arrayNodes) {
        arraysPaths.add(arrayNode.generatePathDotNotation());
      }
      int i = 0;
      boolean finished = false;
      while (!finished) {
        // Check if the path that we are generating exists.
        // We only do it for i > 0 because i = 0 will be checked by the "generateArffInstance" method. Then, if i=0 does
        // not exist for a particular array (e.g. Publication), the "generateArffInstance" method will generate a
        // missing value ("?" in ARFF) for all the fields whose path contains the array with that index
        // (e.g., Publication[0].title)
        // TODO: it may be a mistake here when dealing with arrays of arrays. Test it
        if (i > 0) {
          // Generate paths in JsonPath format for all nodes from the root to the current node and check if they exist
          String jsonPath = "$";
          String path = "";
          TemplateNode currentArrayNode = arrayNodes.get(indexOfCurrentArray);
          for (String key : currentArrayNode.getPath()) {
            jsonPath = jsonPath + ("['" + key + "']");
            path = generatePathDotNotation(path, key);
            // If it is an array, concat index
            if (arraysPaths.contains(path)) {
              int index = arraysPaths.indexOf(path);
              if (index != indexOfCurrentArray) {
                jsonPath = jsonPath + ("[" + arraysIndexes.get(index) + "]");
              } else { // the last index cannot be retrieved from arraysIndexes because it has not been updated yet. It
                // is actually the one that we want to test for existence
                jsonPath = jsonPath + ("[" + i + "]");
              }
              // Query the instance to check if the path exists
              try {
                JsonPath.read(templateInstanceDocument, jsonPath);
              } catch (PathNotFoundException e) {
                finished = true;
              }
            }
          }
        }
        // END
        if (!finished) {
          arraysIndexes.set(indexOfCurrentArray, i);
          // check if it is the last level
          if (indexOfCurrentArray == arraysPaths.size() - 1) {
            ArffInstance arffInstance = generateArffInstance(templateInstanceDocument, fieldNodes, arraysPaths,
                arraysIndexes);
            results.add(arffInstance);
          } else {
            // if the path exists, continue
            generateArffInstances(templateInstanceDocument, fieldNodes, arrayNodes, arraysIndexes,
                indexOfCurrentArray + 1, results);
          }
        }
        i++;
      }
    }
    return results;
  }

  /**
   * Generates an ARFF instance based on all the ARFF attributes defined by the "fields" attribute. If the instance
   * contains arrays, the "arraysIndexes" specifies the particular indexes that will be accessed. For example, for an
   * instance with two arrays, Publication and Address, arraysIndexes may specify that only Publication[1].Title,
   * Publication[1].Year and Address[2].Street, Address[2].City will be returned by a particular call to this method.
   *
   * @param templateInstanceDocument
   * @param fields
   * @param arraysPaths              List of array paths (e.g., Researcher.Publications, Addresses)
   * @param arraysIndexes            List of array indexes to be accessed. Every position in the list corresponds to
   *                                 the same position in the list of array paths. For example, arraysIndexes = [1,2]
   *                                 will
   *                                 mean that the method will access Researcher.Publications[1] and Addresses[2]
   * @return
   */
  private static ArffInstance generateArffInstance(Object templateInstanceDocument, List<TemplateNode> fields,
                                                   List<String> arraysPaths, List<Integer> arraysIndexes) {

    List<String> attValues = new ArrayList<>(); // Array of attribute values that will be used to build the ARFF
    // instance

    // Build the JsonPath expression
    for (TemplateNode field : fields) {
      String jsonPath = "$";
      // Analyze the field path to check if it contains arrays. For every array we need to specify an index (e.g., a[2])
      String path = "";
      for (String key : field.getPath()) {
        jsonPath = jsonPath + ("['" + key + "']");
        // If it is an array, concat index
        path = generatePathDotNotation(path, key);
        if (arraysPaths != null && arraysPaths.contains(path)) {
          int index = arraysPaths.indexOf(path);
          jsonPath = jsonPath + ("[" + arraysIndexes.get(index) + "]");
        }
      }
      // Query the instance to extract the value
      //System.out.println("Path: " + jsonPath);
      try {
        Map attValueMap = JsonPath.read(templateInstanceDocument, jsonPath);
        Optional<ArffAttributeValue> attValue = CedarUtils.getValueOfField(attValueMap);
        if (attValue.isPresent()) {
          attValues.add(attValue.get().getArffValueString()); // Escape single quote
        } else {
          attValues.add(ARFF_MISSING_VALUE); // If the field value is null we store a missing value
        }
      } catch (PathNotFoundException e) { // If the array has not been defined we store a missing value
        attValues.add(ARFF_MISSING_VALUE);
      } catch (IOException e) { // If there was no label defined for an ontology term
        attValues.add(ARFF_MISSING_VALUE);
      }
    }
    return new ArffInstance(attValues);
  }

  /**
   * Generates the path to the nodeKey using dot notation
   *
   * @param nodeKey
   * @param basePath
   * @return
   */
  public static String generatePathDotNotation(String basePath, String nodeKey) {
    String result = "";
    if (basePath.trim().length() == 0) {
      result = result.concat(nodeKey);
    } else {
      result = result.concat(basePath).concat(".").concat(nodeKey);
    }
    return result;
  }

  /**
   * Generates the path using dot notation
   *
   * @param path
   * @return
   */
  public static String generatePathDotNotation(List<String> path) {
    String result = "";
    for (String key : path) {
      if (result.trim().length() > 0) {
        result = result.concat(".");
      }
      result = result.concat(key);
    }
    return result;
  }

  /**
   * Generates the attribute name in a custom format ([term uri](field name))
   *
   * @param node
   * @param dataType
   * @return
   * @throws UnsupportedEncodingException
   */
  public static String toWekaAttributeFormat(TemplateNode node, String dataType) throws UnsupportedEncodingException {
    String instanceType = "";
    if (node.getInstanceType().isPresent()) {
      instanceType = node.getInstanceType().get();
      instanceType = CedarUtils.getTermPreferredUri(instanceType);
    }
    String pathDotNotation = generatePathDotNotation(node.getPath());
    String result = "@attribute '[" + instanceType + "](" + pathDotNotation + ")'" + dataType;
    return result;
  }

  /**
   * Applies the Weka's StringToNominal filter to all the data
   *
   * @param data
   * @return
   */
  public static Instances applyStringToNominalFilter(Instances data) throws Exception {

    StringToNominal stringToNominalFilter = new StringToNominal();
    // Set filter options
    stringToNominalFilter.setAttributeRange("first-last");
    stringToNominalFilter.setInputFormat(data);
    // Apply the filter
    Instances filteredData = Filter.useFilter(data, stringToNominalFilter);
    return filteredData;
  }

  /**
   * Runs the Apriori algorithm
   *
   * @param data        Instances data
   * @param numRules    Maximum number of rules that will be generated
   * @param verboseMode Report progress iteratively
   * @return
   */
  public static Apriori runApriori(Instances data, int numRules, boolean verboseMode) throws Exception {
    Apriori aprioriObj = new Apriori();

    aprioriObj.setVerbose(verboseMode);

    // Set minimum support
    double support = (double) MIN_SUPPORTING_INSTANCES / (double) data.numInstances();
    logger.info("Support that will be used: " + support);
    aprioriObj.setLowerBoundMinSupport(support);

    // Set the threshold for the other metric
    // 0 = confidence | 1 = lift | 2 = leverage | 3 = Conviction
    aprioriObj.setMetricType(new SelectedTag(METRIC_TYPE_ID, Apriori.TAGS_SELECTION));
    if (METRIC_TYPE_ID == 0) { // Confidence
      aprioriObj.setMinMetric(MIN_CONFIDENCE);
    } else if (METRIC_TYPE_ID == 1) { // Lift
      aprioriObj.setMinMetric(MIN_LIFT);
    } else if (METRIC_TYPE_ID == 2) { // Leverage
      aprioriObj.setMinMetric(MIN_LEVERAGE);
    } else { // Conviction
      aprioriObj.setMinMetric(MIN_CONVICTION);
    }

    aprioriObj.setNumRules(numRules);
    aprioriObj.buildAssociations(data);
    return aprioriObj;
  }

  public static boolean ruleMatchesRequirements(AssociationRule rule, Map<String, String> fieldValues, Field
      targetField) {

    // We check the consequence first because it is faster than checking the premise, because we limit the check to
    // those rules that have only 1 attribute in the consequence
    boolean targetFieldFound = false;
    if (rule.getConsequence().size() == 1) {
      List<Item> consequenceItems = new ArrayList(rule.getConsequence());
      for (Item consequenceItem : consequenceItems) {
        String attributeName = consequenceItem.getAttribute().name().toLowerCase();
        if (targetField.getFieldPath().toLowerCase().equals(attributeName)) {
          targetFieldFound = true;
        }
      }
      if (!targetFieldFound) {
        return false;
      }
    } else {
      return false;
    }

    // Check premise
    if (rule.getPremise().size() == fieldValues.size()) {
      List<Item> premiseItems = new ArrayList(rule.getPremise());
      for (Item premiseItem : premiseItems) {
        String attributeName = premiseItem.getAttribute().name().toLowerCase();
        String attributeValue = premiseItem.getItemValueAsString().toLowerCase();
        if (!fieldValues.containsKey(attributeName) || !fieldValues.get(attributeName).toLowerCase().equals
            (attributeValue)) {
          return false;
        }
      }
    } else {
      return false;
    }
    return true;
  }

  /**
   * Filters a list of rules by the number of consequences
   *
   * @param rules
   * @param maxNumberOfConsequences
   * @return
   */
  public static List<EsRule> filterRulesByNumberOfConsequences(List<EsRule> rules, int maxNumberOfConsequences) {
    List<EsRule> outputRules = new ArrayList<>();
    for (EsRule rule : rules) {
      if (rule.getConsequenceSize() <= maxNumberOfConsequences) {
        outputRules.add(rule);
      }
    }
    return outputRules;
  }

  /**
   * @param aprioriResults results of applying the Apriori algorithm using Weka
   * @param templateId     template identifier
   * @return rules for a particular template, represented in our own custom format that is appropriate for
   * Elasticsearch storage and query
   */
  public static List<EsRule> toEsRules(Apriori aprioriResults, String templateId) throws Exception {

    List<AssociationRule> aprioriRules = aprioriResults.getAssociationRules().getRules();
    List<EsRule> esRules = new ArrayList<>();

    for (AssociationRule rule : aprioriRules) {
      // Check that the relevant metrics exist
      List<String> metricNames = Arrays.asList(rule.getMetricNamesForRule());
      if (!metricNames.contains(CONFIDENCE_METRIC_NAME)) {
        throw new Exception("Metric not found: " + CONFIDENCE_METRIC_NAME);
      }
      if (!metricNames.contains(LIFT_METRIC_NAME)) {
        throw new Exception("Metric not found: " + LIFT_METRIC_NAME);
      }
      if (!metricNames.contains(LEVERAGE_METRIC_NAME)) {
        throw new Exception("Metric not found: " + LEVERAGE_METRIC_NAME);
      }
      if (!metricNames.contains(CONFIDENCE_METRIC_NAME)) {
        throw new Exception("Metric not found: " + CONVICTION_METRIC_NAME);
      }

      // Build premise
      List<EsRuleItem> esPremise = new ArrayList<>();
      Iterator<Item> itPremise = rule.getPremise().iterator();
      while (itPremise.hasNext()) {
        Item item = itPremise.next();
        esPremise.add(buildEsRuleItem(item));
      }

      // Build consequence
      List<EsRuleItem> esConsequence = new ArrayList<>();
      Iterator<Item> itConsequence = rule.getConsequence().iterator();
      while (itConsequence.hasNext()) {
        Item item = itConsequence.next();
        esConsequence.add(buildEsRuleItem(item));
      }

      EsRule esRule = new EsRule(templateId, esPremise, esConsequence, rule.getTotalSupport(),
          rule.getNamedMetricValue(CONFIDENCE_METRIC_NAME),
          rule.getNamedMetricValue(LIFT_METRIC_NAME),
          rule.getNamedMetricValue(LEVERAGE_METRIC_NAME),
          rule.getNamedMetricValue(CONFIDENCE_METRIC_NAME),
          esPremise.size(), esConsequence.size());

      esRules.add(esRule);
    }

    return esRules;
  }

  /**
   * Builds an EsRuleItem object form an Item object
   *
   * @param item
   * @return
   * @throws Exception
   */
  public static EsRuleItem buildEsRuleItem(Item item) throws Exception {
    String fieldPath = getEsItemFieldPath(item);
    String fieldType = getEsItemFieldType(item).orElse(null);
    String fieldNormalizedPath = getEsItemFieldNormalizedPath(fieldPath, fieldType);
    List<String> fieldTypeMappings = getEsItemFieldTypeMappings(fieldNormalizedPath, fieldType);
    String fieldValueType = getEsItemFieldValueType(item).orElse(null);
    String fieldValueLabel = getEsItemFieldValueLabel(item);
    String fieldNormalizedValue = getEsItemFieldNormalizedValue(item);
    List<String> fieldValueMappings = getEsItemFieldValueMappings(fieldNormalizedValue);
    String fieldValueResult = FieldValueResultUtils.toValueResultString(fieldValueType, fieldValueLabel);

    return new EsRuleItem(fieldPath, fieldType, fieldNormalizedPath, fieldTypeMappings,
        fieldValueType, fieldValueLabel, fieldNormalizedValue, fieldValueMappings, fieldValueResult);
  }

  /**
   * @param item
   * @return The field name
   */
  public static String getEsItemFieldPath(Item item) throws Exception {
    String attributeName = item.getAttribute().name();
    String separator = "](";
    // Note that attributeName follows the format: ('[fieldType](fieldPath)')
    int index = attributeName.indexOf(separator);
    if (index == -1) {
      logger.error("Separator not found in: " + attributeName);
    }
    String path = attributeName.substring(index + separator.length(), attributeName.length() - 1);
    return path;
  }

  /**
   * @param item
   * @return The URI of the controlled term that annotates the field
   */
  public static Optional<String> getEsItemFieldType(Item item) {
    String attributeName = item.getAttribute().name();
    String separator = "](";
    // Note that attributeName follows the format: ('[fieldType](fieldPath)')
    int index = attributeName.indexOf(separator);
    if (index == -1) {
      logger.error("Separator not found in: " + attributeName);
    }
    String fieldType = attributeName.substring(1, index);
    // e.g. when attribute is [http://purl.obolibrary.org/obo/UBERON_0001870](FRONTAL CORTEX)
    if (fieldType != null && !fieldType.isEmpty()) {
      return Optional.of(fieldType); // e.g. http://purl.obolibrary.org/obo/UBERON_0001870
    }
    // e.g. when attribute is [](frontal cortex)
    else {
      return Optional.empty();
    }
  }

  public static String getEsItemFieldNormalizedPath(String fieldPath, String fieldType)
      throws CedarProcessingException {
    if (fieldType != null && fieldType.length() > 0) {
      return fieldType;
    }
    else if (fieldPath != null && fieldPath.length() > 0) {
      return CedarTextUtils.normalizePath(fieldPath);
    }
    else { // None of them are present
      throw new CedarProcessingException("Either fieldPath or fieldType is required");
    }
  }

  public static List<String> getEsItemFieldTypeMappings(String fieldNormalizedPath, String fieldType) {
    if (fieldType != null && fieldType.length() > 0) {
      return MappingsService.getMappings(fieldNormalizedPath, false);
    }
    else {
      return new ArrayList<>();
    }
  }

  /**
   * @param item
   * @return The field value (i.e., @value or @id)
   */
  public static String getEsItemFieldValue(Item item) {
    Optional<String> valueType = getEsItemFieldValueType(item);
    if (valueType.isPresent() && valueType.get().length() > 0) {  // e.g., when attribute value is [http://purl.obolibrary.org/obo/PATO_0000384](MALE)
      return valueType.get(); // e.g., http://purl.obolibrary.org/obo/PATO_0000384
    }
    else {
      return getEsItemFieldValueLabel(item);
    }
  }

  public static List<String> getEsItemFieldValueMappings(String fieldNormalizedValue) {
    return MappingsService.getMappings(fieldNormalizedValue, false);
  }

  /**
   * @param item
   * @return For ontology terms, the term uri (i.e., @id)
   */
  public static Optional<String> getEsItemFieldValueType(Item item) {
    String attributeFullValue = item.getItemValueAsString();
    String separator = "](";
    // Note that the attribute value follows the format: ('[fieldValueType](fieldValue)')
    int index = attributeFullValue.indexOf(separator);
    if (index == -1) {
      logger.error("Separator not found in: " + attributeFullValue);
    }
    String valueType = attributeFullValue.substring(1, index);

    // e.g. when attribute value is [http://purl.obolibrary.org/obo/PATO_0000384](MALE)
    if (valueType != null && !valueType.isEmpty()) {
      return Optional.of(valueType); // e.g. http://purl.obolibrary.org/obo/PATO_0000384
    }
    // e.g. when attribute is [](MALE)
    else {
      return Optional.empty();
    }
  }

  /**
   * @param item
   * @return The field value label (i.e., @value or rdfs:label)
   */
  public static String getEsItemFieldValueLabel(Item item) {
      String attributeFullValue = item.getItemValueAsString();
      String separator = "](";
      // Note that the attribute value follows the format: ('[fieldValueType](fieldValue)') and that we consider that
      // fieldValue will be always the label
      int index = attributeFullValue.indexOf(separator);
      if (index == -1) {
        logger.error("Separator not found in: " + attributeFullValue);
      }
      String value = attributeFullValue.substring(index + separator.length(), attributeFullValue.length() - 1);

      if (value.length() > 0) {
        return value; // e.g. MALE
      }
      // e.g. when attribute value is []()
      else {
        return null;
    }
  }

  /**
   * @param item
   * @return The field normalized value. For ontology terms, it returns the term uri. For free text values, it
   * returns the value after applying a basic normalization
   */
  public static String getEsItemFieldNormalizedValue(Item item) {
    Optional<String> valueType = getEsItemFieldValueType(item);
    if (valueType.isPresent() && valueType.get().length() > 0) {
      return valueType.get();
    } else {
      return CedarTextUtils.normalizeValue(getEsItemFieldValue(item));
    }
  }

  public static String ruleItemsToString(List<EsRuleItem> ruleItems, boolean showNullTypes) {
    String result = "";
    boolean firstPremiseItem = true;
    for (EsRuleItem ruleItem : ruleItems) {
      if (!firstPremiseItem) {
        result += " AND ";
      }
      result += ruleItem.toPrettyString(false);
      firstPremiseItem = false;
    }
    return result;
  }

}
