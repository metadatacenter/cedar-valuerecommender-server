package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.MongoClient;
import com.sun.jdi.ArrayReference;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import weka.associations.Apriori;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import javax.management.InstanceNotFoundException;
import javax.swing.text.TabExpander;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.ITEMS_FIELD_NAME;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.MAX_ARRAY_ITEMS;
import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.TYPE_FIELD_NAME;

/**
 * Utilities to generate and manage association rules using Weka
 */
public class AssociationRulesUtils {

  private static ElasticsearchQueryService esQueryService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private static TemplateService<String, JsonNode> templateService;

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
      // TODO: log the exception
      e.printStackTrace();
    }
  }

  /**
   * Returns basic information of all template nodes (elements and fields)
   */
  public static List<TemplateNode> getTemplateNodes(JsonNode template, List<String> currentPath, List results) {
    if (currentPath == null) {
      currentPath = new ArrayList<>();
    }
    if (results == null) {
      results = new ArrayList();
    }
    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = template.fields();
    while (fieldsIterator.hasNext()) {
      Map.Entry<String, JsonNode> field = fieldsIterator.next();
      final String fieldKey = field.getKey();
      if (field.getValue().isContainerNode()) {
        JsonNode fieldNode;
        boolean isArray;
        // Single-instance node
        if (!field.getValue().has(ITEMS_FIELD_NAME)) {
          fieldNode = field.getValue();
          isArray = false;
        }
        // Multi-instance node
        else {
          fieldNode = field.getValue().get(ITEMS_FIELD_NAME);
          isArray = true;
        }
        // Field
        if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals(CedarNodeType
            .FIELD.getAtType())) {
          // Add field path to the results. I create a new list to not modify currentPath
          List<String> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(fieldKey);
          results.add(new TemplateNode(fieldKey, fieldPath, CedarNodeType.FIELD, isArray));
        }
        // Element
        else if (fieldNode.get(TYPE_FIELD_NAME) != null && fieldNode.get(TYPE_FIELD_NAME).asText().equals
            (CedarNodeType.ELEMENT.getAtType())) {
          List<String> fieldPath = new ArrayList<>(currentPath);
          fieldPath.add(fieldKey);
          results.add(new TemplateNode(fieldKey, fieldPath, CedarNodeType.ELEMENT, isArray));
          getTemplateNodes(fieldNode, fieldPath, results);
        }
        // All other nodes
        else {
          getTemplateNodes(fieldNode, currentPath, results);
        }
      }
    }
    return results;
  }

  /**
   * Generates an ARFF file with the instances for a particular template.
   *
   * @param templateId
   * @return The name of the ARFF file that was created
   * @throws IOException
   * @throws ProcessingException
   */
  public static String generateInstancesFile(String templateId) throws IOException, ProcessingException,
      InstanceNotFoundException {
    // TODO: store the file in an appropriate location
    String fileName = templateId.substring(templateId.lastIndexOf("/") + 1) + ".arff";
    FileWriter fw = new FileWriter(fileName);
    BufferedWriter bw = new BufferedWriter(fw);
    PrintWriter out = new PrintWriter(bw);

    out.println("% ARFF file for CEDAR template id = " + templateId);
    out.println("\n@relation example\n");

    // 1. Get instance attributes
    JsonNode template = templateService.findTemplate(templateId);
    if (template == null) {
      throw new InstanceNotFoundException("Template not found (id=" + templateId + ")");
    }
    List<TemplateNode> nodes = AssociationRulesUtils.getTemplateNodes(template, null, null);

    // Field nodes
    List<TemplateNode> fieldNodes = new ArrayList<>();
    for (TemplateNode node : nodes) {
      if (node.getType().equals(CedarNodeType.FIELD)) {
        fieldNodes.add(node);
      }
    }

    // Array nodes
    List<TemplateNode> arrayNodes = new ArrayList<>();
    for (TemplateNode node : nodes) {
      if (node.isArray()) {
        arrayNodes.add(node);
      }
    }

    // Generate ARFF attributes
    for (TemplateNode node : fieldNodes) {
      if (node.getType().equals(CedarNodeType.FIELD)) {
        out.println("@attribute " + toWekaAttributeFormat(node.getPath()) + " string");
      }
    }

    // 2. Get template instances in ARFF format
    out.println("\n@data");
    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);

    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      // Transform the template instances to a list of ARFF instances
      List<ArffInstance> arffInstances = AssociationRulesUtils.instanceToArffInstances(templateInstance, fieldNodes,
          arrayNodes);
      System.out.println("** Arff Instances **");
      for (ArffInstance instance : arffInstances) {
        out.println(instance.toArffFormat());
      }
      System.out.println("*************");
    }
    out.close();

    return fileName;
  }

  public static List<ArffInstance> instanceToArffInstances(JsonNode templateInstance, List<TemplateNode> fields,
                                                           List<TemplateNode> arrayNodes) {

    Map<String, Integer> arraysIndexes = new LinkedHashMap<>();

    Integer initialIndex = 0;
    for (TemplateNode arrayNode : arrayNodes) {
      arraysIndexes.put(arrayNode.generatePath(), initialIndex);
    }

    generateArffInstances(templateInstance, fields, new ArrayList(arraysIndexes.keySet()), new ArrayList
        (arraysIndexes.values()), 0);

    return null;
  }

  private static ArffInstance generateArffInstances(JsonNode templateInstance, List<TemplateNode> fields,
                                                        List<String> arraysKeys, List<Integer> arraysIndexes, int
                                                            currentKeyIndex) {
    //indexes.set(i, (indexes.get(i) + 1));
    int i = 0;
    int max = 3;

    while (i < max) {
      arraysIndexes.set(currentKeyIndex, i);
      // check if it is the last level
      if (currentKeyIndex == arraysKeys.size() - 1) {
        System.out.println(arraysIndexes.toString());
        generateArffInstance(templateInstance, fields, arraysKeys, arraysIndexes);
      } else {
        generateArffInstances(templateInstance, fields, arraysKeys, arraysIndexes, currentKeyIndex + 1);
      }
      i++;

    }
    return null;
  }

  private static ArffInstance generateArffInstance(JsonNode templateInstance, List<TemplateNode> fields, List<String> arraysKeys, List<Integer> arraysIndexes) {

    List<String> attValues = new ArrayList<>();
    Object document = Configuration.defaultConfiguration().jsonProvider().parse(templateInstance.toString());

    // Build the JsonPath expression
    for (TemplateNode field : fields) {
      String jsonPath = "$";
      // Analyze the field path to check if it contains an array where we need to put an index
      String path = "";
      for (String key : field.getPath()) {
        if (path.length() == 0) {
          path = path.concat(key);
        } else {
          path = path.concat(".").concat(key);
        }
        jsonPath = jsonPath + ("['" + key + "']");
        // If it is an array, concat index
        if (arraysKeys.contains(path)) {
          int index = arraysKeys.indexOf(path);
          jsonPath = jsonPath + ("[" + arraysIndexes.get(index) + "]");
        }
      }
      // Query the instance to extract the value
      try {
        System.out.println(jsonPath);
        Map attValueMap = JsonPath.read(document, jsonPath);
        String attValue = "'" + CedarUtils.getValueOfField(attValueMap).get() + "'";
        attValues.add(attValue);
      } catch (PathNotFoundException e) {
        // TODO
      }
    }
    return new ArffInstance(attValues);
  }

//  private static ArffInstance generateArffInstance(JsonNode templateInstance, Map<String, Integer>
// arrayNodeIndexes, Map.Entry<String, Integer> currentNode, int currentNodeIndex, int currentIndex) {
//    ArrayList<Map.Entry<String, Integer>> arrayNodesList = new ArrayList(arrayNodeIndexes.entrySet());//TODO: wrong
//    System.out.println("==============");
//    System.out.println("current node index: " + currentNodeIndex);
//    System.out.println(currentNode.getKey());
//
//    int max = 2;
//    boolean finished = false;
//    while (!finished) {
//      if (currentNodeIndex + 1 < arrayNodesList.size()) { // check that next entry exists
//        // TODO
//        currentNodeIndex = currentNodeIndex + 1;
//        currentNode = arrayNodesList.get(currentNodeIndex);
//        generateArffInstance(templateInstance, arrayNodeIndexes, currentNode, currentNodeIndex, currentIndex);
//      } else {
//        System.out.println("This is the last one. Array indexes generated:");
//        System.out.println(arrayNodeIndexes.values().toString());
//        finished = true;
//      }
//    }
//    return null;
//  }


  /**
   * @return The field path using Weka's attribute format (e.g., 'Address.Zip Code')
   */
  public static String toWekaAttributeFormat(List<String> path) {
    String result = "";
    for (String key : path) {
      if (result.trim().length() > 0) {
        result = result.concat(".");
      }
      result = result.concat(key);
    }
    return "'" + result + "'";
  }

  /**
   * Generates an instance in Arff format
   *
   * @return
   */
//  public static String instanceToArff(JsonNode templateInstance, List<Attribute> attributes) {
//    String result = "";
//    Object document = Configuration.defaultConfiguration().jsonProvider().parse(templateInstance.toString());
//    for (Attribute att : attributes) {
//      String attPath = att.toJsonPathFormat();
//      List<Map> attMaps = new ArrayList<>();
//      // Returns an array
//      if (att.generatesArrayResult()) {
//        attMaps = JsonPath.read(document, attPath);
//      }
//      // Returns a single object
//      else {
//        Map attMap = JsonPath.read(document, attPath);
//        attMaps.add(attMap);
//      }
//      for (Map attMap : attMaps) {
//        String attValue = "'" + CedarUtils.getValueOfField(attMap).get() + "'";
//        if (result.trim().length() == 0) {
//          result = attValue;
//        } else {
//          result = result + "," + attValue;
//        }
//      }
//    }
//    return result;
//  }


  /**
   * Note than a template instance can generate multiple Arff instances because of all the combinations generated by
   * the arrays
   */
//  public static List<ArffInstance> instanceToArffInstances(JsonNode templateInstance, List<Attribute> attributes,
//                                                           List<String> currentPath, List<ArffInstance> results,
//                                                           ArffInstance currentResult, Integer attributesCount,
// boolean isArray, List<String> excludedPath) {
//
//
//    if (attributesCount == null) {
//      attributesCount = 0;
//    }
//
//    if (currentPath == null) {
//      currentPath = new ArrayList<>();
//    }
//
//    if (results == null) {
//      results = new ArrayList<>();
//    }
//
//    if (currentResult == null) {
//      currentResult = new ArffInstance();
//    }
//
//    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = templateInstance.fields();
//    while (fieldsIterator.hasNext()) {
//      Map.Entry<String, JsonNode> field = fieldsIterator.next();
//      if (field.getValue().isContainerNode()) {
//        if (!field.getKey().equals("@context")) {
//          // Single value (template field or template element)
//          if (field.getValue().isObject()) {
//            // If it is a Template Field (single instance)
//
//            // TODO: note that it is commented out
//            if (isTemplateField(attributes, currentPath, field.getKey())) {
//
//              System.out.println("CURRENT PATH: " + Arrays.toString(currentPath.toArray()) + " " + field.getKey());
//              currentResult.addValue(CedarUtils.getValueOfField(field.getValue()).get());
//              System.out.println("Current result: " + currentResult.toArffFormat());
//              attributesCount++;
//
//              if (isArray) {
//                instanceToArffInstances(templateInstance, attributes, null, results, currentResult,
// attributesCount, true, currentPath);
//              }
//
//              // last attribute
////              if (attributesCount == attributes.size()) {
////                System.out.println("New result because attributesCount = " + attributesCount);
////                results.add(currentResult);
////                currentResult = "";
////                attributesCount = 0;
////              }
//              // It is a Template Element
//            } else {
//              List<String> newPath = new ArrayList<>(currentPath);
//              newPath.add(field.getKey());
//              instanceToArffInstances(field.getValue(), attributes, newPath, results, currentResult,
// attributesCount, false, null);
//            }
//          }
//          // Array
//          else if (field.getValue().isArray()) {
//            for (int i = 0; i < field.getValue().size(); i++) {
//
//              System.out.println(field.getKey() + "[" + i + "]");
//              JsonNode arrayItem = field.getValue().get(i);
//              List<String> newPath = new ArrayList<>(currentPath);
//              newPath.add(field.getKey());
//              instanceToArffInstances(arrayItem, attributes, newPath, results, currentResult, attributesCount,
// true, null);
//            }
//
//
//
//          }
//        }
//      }
//    }
//    return results;
//  }
  public static boolean isTemplateField(List<TemplateNode> fieldNodes, List<String> path, String fieldKey) {
    List<String> fieldPath = new ArrayList<>();
    fieldPath.addAll(path);
    fieldPath.add(fieldKey);
    for (TemplateNode fieldNode : fieldNodes) {
      if (fieldNode.getPath().equals(fieldPath)) {
        return true;
      }
    }
    return false;
  }

//  public static boolean isLastAttribute(List<Attribute> attributes, List<String> path, String fieldKey) {
//    List<String> fieldPath = new ArrayList<>();
//    fieldPath.addAll(path);
//    fieldPath.add(fieldKey);
//    Attribute last = attributes.get(attributes.size()-1);
//    if (last.toWekaAttributeFormat().equals(new Attribute(fieldPath).toWekaAttributeFormat())) {
//      return true;
//    }
//    return false;
//  }

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
   * @param data
   * @param numRules
   * @return
   */
  public static Apriori runApriori(Instances data, int numRules) throws Exception {
    Apriori aprioriObj = new Apriori();
    aprioriObj.setNumRules(numRules);
    aprioriObj.buildAssociations(data);
    return aprioriObj;
  }

  //  /**
//   * Translates a template instance to Weka's ARFF format (https://www.cs.waikato.ac.nz/ml/weka/arff.html). It returns
//   * the line for the given template instance.
//   *
//   * @param templateInstance
//   */
//  public static void templateInstanceToArff(JsonNode templateInstance, JsonNode templateSummary) throws IOException {
//    // TODO: there is no need to generate the field keys n times
//    final Map<String, String> fieldPathsAndValues = templateInstanceToMap(templateInstance, templateSummary, null,
// null);
//  }
//
//  // Returns a map with all field paths and the corresponding values (i.e., fieldPath -> fieldValue)
//  private static Map<String, String> templateInstanceToMap(JsonNode templateInstance, JsonNode schemaSummary, String
//      path, Map<String, String> results) {
//
//    if (path == null) {
//      path = "";
//    }
//
//    if (results == null) {
//      results = new LinkedHashMap<>();
//    }
//
//    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = templateInstance.fields();
//    while (fieldsIterator.hasNext()) {
//      Map.Entry<String, JsonNode> field = fieldsIterator.next();
//      if (field.getValue().isContainerNode()) {
//        if (!field.getKey().equals("@context")) {
//          // Single value
//          if (field.getValue().isObject()) {
//            // If it is a Template Field (single instance)
//            if (isTemplateField(field.getKey(), schemaSummary)) {
//              JsonNode fieldSchema = null;
//              if (schemaSummary != null && schemaSummary.has(field.getKey() + FIELD_SUFFIX)) {
//                fieldSchema = schemaSummary.get(field.getKey() + FIELD_SUFFIX);
//              }
//              if (fieldSchema != null) {
//                results.put(getValidFieldKey(path, field.getKey(), null), getFieldValue(field.getValue()));
//              }
//              // It is a Template Element
//            } else if (isTemplateElement(field.getKey(), schemaSummary)) {
//              String newPath = getValidFieldKey(path, field.getKey(), null);
//              templateInstanceToMap(field.getValue(), schemaSummary.get(field.getKey()), newPath, results);
//            }
//          }
//          // Array
//          else if (field.getValue().isArray()) {
//            //path = getValidFieldKey(path, field.getKey(), null);
//            for (int i = 0; i < field.getValue().size(); i++) {
//              JsonNode arrayItem = field.getValue().get(i);
//              // It is a Template Field (multi-instance)
//              if (isTemplateField(field.getKey(), schemaSummary)) {
//                JsonNode fieldSchema = null;
//                if (schemaSummary != null && schemaSummary.has(field.getKey() + FIELD_SUFFIX)) {
//                  fieldSchema = schemaSummary.get(field.getKey() + FIELD_SUFFIX);
//                }
//                // If the field was not found in the template, it is ignored. It may happen when template updated
//                if (fieldSchema != null) {
//                  results.put(getValidFieldKey(path, field.getKey(), i), getFieldValue(field.getValue()));
//                }
//              }
//              // It is a Template Element (multi-instance)
//              else if (isTemplateElement(field.getKey(), schemaSummary)) {
//                String newPath = getValidFieldKey(path, field.getKey(), i);
//                templateInstanceToMap(arrayItem, schemaSummary.get(field.getKey()), newPath, results);
//              }
//            }
//          }
//        }
//      }
//    }
//    return results;
//  }
//
//  // Checks if JSON the field is a template field using information from the template
//  private static boolean isTemplateField(String fieldName, JsonNode schemaSummary) {
//    String templateFieldName = fieldName + FIELD_SUFFIX;
//    if (schemaSummary.has(templateFieldName)) {
//      return true; // It is a template field
//    } else {
//      return false;
//    }
//  }
//
//  // Checks if JSON the field is a template element using information from the template
//  private static boolean isTemplateElement(String fieldName, JsonNode schemaSummary) {
//    if (schemaSummary.has(fieldName)) {
//      return true; // It is a template element
//    } else {
//      return false;
//    }
//  }
//
//  // Generates a field key that can be used to refer to the field in the ARFF file
//  private static String getValidFieldKey(String path, String fieldKey, Integer arrayPosition) {
//    String prefix = "";
//    if (path != null && path.trim().length() > 0) {
//      prefix = path + ".";
//    }
//    String suffix = "";
//    if (arrayPosition != null) {
//      suffix = "[" + arrayPosition.toString() + "]";
//    }
//    return prefix + "'" + fieldKey + "'" + suffix;
//  }
//
  private static String getFieldValue(JsonNode field) {
    String fieldValueName = getFieldValueName(field);
    return field.get(fieldValueName).textValue();
  }

  private static String getFieldValueName(JsonNode item) {
    if (item.has("@value")) {
      return "@value";
    }
    return "@id";
  }

}
