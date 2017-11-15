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
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class AssociationRulesService implements IAssociationRulesService {

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

  public void generateRulesForTemplate(String templateId) throws IOException, ProcessingException {

    // 1. Generate ARFF file for template
    Instances data = readTemplateData(templateId);

    // 2. Read the ARFF file and generate rules

  }

  private Instances readTemplateData(String templateId) throws IOException, ProcessingException {

    List<Attribute> attributes = getWekaAttributes(templateId);
    Instances instances = getWekaInstances(templateId, attributes);

    // How does Weka generate the rules? We may not need the ARFF file


    // 1. Extract list of field paths from the template
    // 2. Use the list of field paths to generate the instances in ARFF

    return null;
  }

  /**
   * Returns a list of Weka attributes for a particular template
   *
   * @param templateId
   * @return
   * @throws IOException
   * @throws ProcessingException
   */
  private List<Attribute> getWekaAttributes(String templateId) throws IOException, ProcessingException {
    JsonNode template = templateService.findTemplate(templateId);
    List<String> fieldPaths = CedarUtils.getTemplateFieldPaths(template, null, null);
    List<Attribute> attributes = new ArrayList<>();
    for (String path : fieldPaths) {
      attributes.add(new Attribute(path, true));
    }
    return attributes;
  }

  private Instances getWekaInstances(String templateId, List<Attribute> attributes) throws IOException {
    Instances data = new Instances("Instances", (ArrayList<Attribute>) attributes, 0);
    // Retrieve all template instances for the template
    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);

    //JsonNode templateSummary = esQueryService.getTemplateSummary(templateId);

    // Iterate over all instances to generate the ARFF file
    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      Instance ins = getWekaInstance(templateInstance, attributes);
    }
    return data;
  }

  private Instance getWekaInstance(JsonNode templateInstance, List<Attribute> attributes) {
    String json = templateInstance.toString();
    Object document = Configuration.defaultConfiguration().jsonProvider().parse(json);
    // Create empty instance
    Instance instance = new DenseInstance(attributes.size());
    // Set instance's values
    for (Attribute att : attributes) {
      String attPath = "$" + att.name();
      String attValue = CedarUtils.getValueOfField((Map) JsonPath.read(document, attPath)).get();
      instance.setValue(att, attValue);
    }

    System.out.println("The instance: " + instance);

    return null;
  }




}
