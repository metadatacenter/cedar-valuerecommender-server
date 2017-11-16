package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.mongodb.MongoClient;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.CedarUtils;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.FieldPath;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import weka.associations.Apriori;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

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
   * @param templateId
   * @return The name of the ARFF file that was created
   * @throws IOException
   * @throws ProcessingException
   */
  public static String generateInstancesFile(String templateId) throws IOException, ProcessingException {
    // TODO: store the file in an appropriate location
    String fileName = templateId.substring(templateId.lastIndexOf("/") + 1) + ".arff";
    FileWriter fw = new FileWriter(fileName);
    BufferedWriter bw = new BufferedWriter(fw);
    PrintWriter out = new PrintWriter(bw);

    out.println("% ARFF file for CEDAR template id = " + templateId);
    out.println("\n@relation example\n");

    // 1. Get instance attributes
    JsonNode template = templateService.findTemplate(templateId);
    List<FieldPath> attributes = AssociationRulesUtils.getAttributes(template);

    for (FieldPath att : attributes) {
      out.println("@attribute " + att.getPath() + " string");
    }

    // 2. Get instances in ARFF format
    out.println("\n@data");
    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);
    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      String instanceArff = AssociationRulesUtils.instanceToArff(templateInstance, attributes);
      out.println(instanceArff);
    }
    out.close();

    return fileName;
  }

  /**
   * Returns a list that contains the paths of the template fields
   *
   * @param template
   * @return
   * @throws IOException
   * @throws ProcessingException
   */
  public static List<FieldPath> getAttributes(JsonNode template) throws IOException, ProcessingException {
    return CedarUtils.getTemplateFieldPaths(template, null, null);
  }

  /**
   * Generates an instance in Arff format
   *
   * @return
   */
  public static String instanceToArff(JsonNode templateInstance, List<FieldPath> attributes) {
    String result = "";
    Object document = Configuration.defaultConfiguration().jsonProvider().parse(templateInstance.toString());
    for (FieldPath att : attributes) {
      String attPath = "$" + att.getPathSquareBrackets();
      String attValue = "'" + CedarUtils.getValueOfField((Map) JsonPath.read(document, attPath)).get() + "'";
      if (result.trim().length() == 0) {
        result = attValue;
      } else {
        result = result + "," + attValue;
      }
    }
    return result;
  }

  /**
   * Applies the Weka's StringToNominal filter to all the data
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
//    final Map<String, String> fieldPathsAndValues = templateInstanceToMap(templateInstance, templateSummary, null, null);
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
//  private static String getFieldValue(JsonNode field) {
//    String fieldValueName = getFieldValueName(field);
//    return field.get(fieldValueName).textValue();
//  }
//
//  private static String getFieldValueName(JsonNode item) {
//    if (item.has("@value")) {
//      return "@value";
//    }
//    return "@id";
//  }

}
