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
import weka.core.SelectedTag;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import javax.management.InstanceNotFoundException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

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
   * Generates an ARFF file with information from all the instances for a given template
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
        if (USE_ALL_FIELDS) { // Use all fields to generate rules
          fieldNodes.add(node);
        }
        else { // Consider only field for which valueRecommendations are enabled
          if (node.isValueRecommendationEnabled()) {
            fieldNodes.add(node);
          }
        }
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
    System.out.println("\nARFF Attributes");
    for (TemplateNode node : fieldNodes) {
      if (node.getType().equals(CedarNodeType.FIELD)) {
        out.println("@attribute " + toWekaAttributeFormat(node.getPath()) + " string");
        System.out.println("@attribute " + toWekaAttributeFormat(node.getPath()) + " string");
      }
    }
    System.out.println();

    // 2. Get template instances in ARFF format
    out.println("\n@data");
    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);

    Map<String, Integer> arraysPathsAndIndexes = new LinkedHashMap<>();
    Integer initialIndex = 0;
    for (TemplateNode arrayNode : arrayNodes) {
      arraysPathsAndIndexes.put(arrayNode.generatePathDotNotation(), initialIndex);
    }

    int count = 0;
    for (String tiId : templateInstancesIds) {
      JsonNode ti = templateInstanceService.findTemplateInstance(tiId);
      Object tiDocument = Configuration.defaultConfiguration().jsonProvider().parse(ti.toString());
      // Transform the template instances to a list of ARFF instances
      List<ArffInstance> arffInstances = generateArffInstances(tiDocument, fieldNodes, arrayNodes, new ArrayList
          (arraysPathsAndIndexes.values()), 0, null);

      for (ArffInstance instance : arffInstances) {
        out.println(instance.toArffFormat());
      }
      count++;
      if (MAX_INSTANCES_FOR_ARM > 0 && count == MAX_INSTANCES_FOR_ARM) {
        break;
      }
    }
    out.close();
    System.out.println("Instances file created. Generating rules...");
    return fileName;
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
   *                                 the same
   *                                 position in the list of array paths. For example, arraysIndexes = [1,2] will
   *                                 mean that
   *                                 the method will access Researcher.Publications[1] and Addresses[2]
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
        Optional<String> attValue = CedarUtils.getValueOfField(attValueMap, true);
        if (attValue.isPresent() && attValue.get().trim().length() > 0) {
          attValues.add("'" + attValue.get().replace("'", "\\'") + "'"); // Escape single quote
        } else {
          attValues.add(ARFF_MISSING_VALUE); // If the field value is null we store a missing value
        }
      } catch (PathNotFoundException e) {
        attValues.add(ARFF_MISSING_VALUE); // If the array has not been defined we store a missing value
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
   * @param data     Instances data
   * @param numRules Maximum number of rules that will be generated
   * @return
   */
  public static Apriori runApriori(Instances data, int numRules) throws Exception {
    Apriori aprioriObj = new Apriori();
    aprioriObj.setLowerBoundMinSupport(0.01);
    aprioriObj.setMinMetric(0.01);



    aprioriObj.setNumRules(numRules);
    aprioriObj.buildAssociations(data);
    return aprioriObj;
  }

}
