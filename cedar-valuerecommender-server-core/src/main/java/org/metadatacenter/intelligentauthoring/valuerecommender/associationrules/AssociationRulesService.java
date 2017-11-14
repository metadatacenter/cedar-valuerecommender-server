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

  static {
    try {
      esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
    } catch (UnknownHostException e) {
      // TODO: log the exception
      e.printStackTrace();
    }
  }

  public void generateRulesForTemplate(String templateId) throws IOException {

    List<String> lines = Arrays.asList("The first line", "The second line");
    Path file = Paths.get("the-file-name.txt");
    Files.write(file, lines, Charset.forName("UTF-8"));

    // 1. Generate ARFF file for template
    // 2. Read the ARFF file and generate rules

    CedarDataServices.initializeMongoClientFactoryForDocuments(ConfigManager.getCedarConfig().getTemplateServerConfig
        ().getMongoConnection());
    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
    MongoConfig templateServerConfig = ConfigManager.getCedarConfig().getTemplateServerConfig();
    TemplateInstanceService<String, JsonNode> templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));

    List<String> templateInstancesIds = esQueryService.getTemplateInstancesIdsByTemplateId(templateId);
    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      JsonNode templateSummary = esQueryService.getTemplateSummary(tiId);
      // TODO: do something with the ARFF
      AssociationRulesUtils.templateInstanceToArff(templateInstance, templateSummary);
    }
  }

  private void templateToArff() {
    // 1. Extract list of field paths from the template
    // 2. Use the list of field paths to generate the instances in ARFF
  }

  private void generateRulesFromArff()
  {

  }

}
