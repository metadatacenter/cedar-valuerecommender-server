package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.MongoClient;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.TemplateNode;
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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.ARFF_MISSING_VALUE;

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
    List<TemplateNode> nodes = CedarUtils.getTemplateNodes(template, null, null);

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


    Map<String, Integer> arraysIndexes = new LinkedHashMap<>();

    Integer initialIndex = 0;
    for (TemplateNode arrayNode : arrayNodes) {
      arraysIndexes.put(arrayNode.generatePath(), initialIndex);
    }


    int i = 0;
    final int MAX_ITERATIONS = 100;
    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      Object templateInstanceDocument = Configuration.defaultConfiguration().jsonProvider().parse(templateInstance
          .toString());
      // Transform the template instances to a list of ARFF instances
      List<ArffInstance> arffInstances = generateArffInstances(templateInstanceDocument, fieldNodes, new ArrayList(arraysIndexes.keySet()), new ArrayList
          (arraysIndexes.values()), arrayNodes, 0, null);

      for (ArffInstance instance : arffInstances) {
        out.println(instance.toArffFormat());
      }
      i++;
      if (MAX_ITERATIONS > 0 && i==MAX_ITERATIONS) {
        break;
      }
    }
    out.close();
    System.out.println("Instances file created. Generating rules...");
    return fileName;
  }

  private static List<ArffInstance> generateArffInstances(Object templateInstanceDocument, List<TemplateNode> fields,
                                                    List<String> arraysKeys, List<Integer> arraysIndexes,
                                                    List<TemplateNode> arrayNodes, int
                                                        currentKeyIndex, List<ArffInstance> results) {
    if (results == null) {
      results = new ArrayList<>();
    }

    int i = 0;

    boolean finished = false;

    while (!finished) {

      if (i > 0) {
        // BEGIN check if the current level exists with that index
        String jsonPath = "$";
        String path = "";
        TemplateNode currentArrayNode = arrayNodes.get(currentKeyIndex);
        for (String key : currentArrayNode.getPath()) {
          jsonPath = jsonPath + ("['" + key + "']");
          // If it is an array, concat index
          path = generatePath(key, path);
          if (arraysKeys.contains(path)) {
            int index = arraysKeys.indexOf(path);

            if (index != currentKeyIndex) {
              jsonPath = jsonPath + ("[" + arraysIndexes.get(index) + "]");
            } else { // the last index cannot be retrieved from arraysIndexes because it has not been updated yet. It
              // is actually the one that we want to test for existence
              jsonPath = jsonPath + ("[" + i + "]");
            }
          }
        }
        // Query the instance to check if the path exists
        try {
          JsonPath.read(templateInstanceDocument, jsonPath);
        } catch (PathNotFoundException e) {
          finished = true;
        }
      }
      // END

      if (!finished) {

        arraysIndexes.set(currentKeyIndex, i);

        // check if it is the last level
        if (currentKeyIndex == arraysKeys.size() - 1) {
          ArffInstance arffInstance = generateArffInstance(templateInstanceDocument, fields, arraysKeys, arraysIndexes);
          results.add(arffInstance);
        } else {

          // if the path exists, continue
          generateArffInstances(templateInstanceDocument, fields, arraysKeys, arraysIndexes, arrayNodes,
              currentKeyIndex + 1, results);

        }

      }
      i++;

    }
    return results;
  }

  private static ArffInstance generateArffInstance(Object templateInstanceDocument, List<TemplateNode> fields,
                                                   List<String> arraysKeys, List<Integer> arraysIndexes) {

    List<String> attValues = new ArrayList<>();

    // Build the JsonPath expression
    for (TemplateNode field : fields) {
      String jsonPath = "$";
      // Analyze the field path to check if it contains an array where we need to put an index
      String path = "";
      for (String key : field.getPath()) {
        jsonPath = jsonPath + ("['" + key + "']");
        // If it is an array, concat index
        path = generatePath(key, path);
        if (arraysKeys.contains(path)) {
          int index = arraysKeys.indexOf(path);
          jsonPath = jsonPath + ("[" + arraysIndexes.get(index) + "]");
        }
      }
      // Query the instance to extract the value
      //System.out.println("Path: " + jsonPath);
      try {
        Map attValueMap = JsonPath.read(templateInstanceDocument, jsonPath);
        Optional<String> attValue = CedarUtils.getValueOfField(attValueMap, true);
        if (attValue.isPresent() && attValue.get().trim().length() > 0) {
          attValues.add(attValue.get().replace("'","\\'"));
        } else {
          attValues.add(ARFF_MISSING_VALUE); // When the field value is null
        }
      }
      catch (PathNotFoundException e) {
        attValues.add(ARFF_MISSING_VALUE); // When the array has not been defined
      }
    }
    return new ArffInstance(attValues);
  }

  public static String generatePath(String nodeKey, String basePath) {
    String path = "";
    if (basePath.length() == 0) {
      path = path.concat(nodeKey);
    } else {
      path = path.concat(".").concat(nodeKey);
    }
    return path;
  }

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
