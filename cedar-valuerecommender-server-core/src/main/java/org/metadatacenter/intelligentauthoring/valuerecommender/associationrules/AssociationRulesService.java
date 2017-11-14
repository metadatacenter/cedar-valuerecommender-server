package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClient;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class AssociationRulesService implements IAssociationRulesService {

  private static ElasticsearchQueryService esQueryService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;

  static {
    try {
      // Initialize ElasticsearchQueryService
      esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
      // Initialize TemplateInstanceServiceMongoDB
      CedarDataServices.initializeMongoClientFactoryForDocuments(ConfigManager.getCedarConfig().getTemplateServerConfig().getMongoConnection());
      MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
      MongoConfig templateServerConfig = ConfigManager.getCedarConfig().getTemplateServerConfig();
      templateInstanceService = new TemplateInstanceServiceMongoDB(mongoClientForDocuments,
          templateServerConfig.getDatabaseName(),
          templateServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));
    } catch (UnknownHostException e) {
      // TODO: log the exception
      e.printStackTrace();
    }
  }

  public void generateRulesForTemplate(String templateId) throws IOException {

    // 1. Generate ARFF file for template
    templateToArff(templateId);

    // 2. Read the ARFF file and generate rules

  }

  private void templateToArff(String templateId) throws IOException {



//    String arffHeader = "% ARFF file for a CEDAR template (id=" + templateId + ")";
//    String shortTemplateId = templateId.substring(templateId.lastIndexOf("/") + 1);
//    String fileName = shortTemplateId + ".arff";
//    Path file = Paths.get(fileName);
//    Files.write(file, arffHeader.getBytes());

    // Retrieve all template instances for the template
    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);
    JsonNode templateSummary = esQueryService.getTemplateSummary(templateId);

    




    // Iterate over all instances to generate the ARFF file
    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      // TODO: do something with the ARFF
      AssociationRulesUtils.templateInstanceToArff(templateInstance, templateSummary);
    }

    // How does Weka generate the rules? We may not need the ARFF file




    // 1. Extract list of field paths from the template
    // 2. Use the list of field paths to generate the instances in ARFF
  }

  private void generateRulesFromArff()
  {

  }

}
