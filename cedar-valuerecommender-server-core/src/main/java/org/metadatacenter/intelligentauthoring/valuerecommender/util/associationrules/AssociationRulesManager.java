package org.metadatacenter.intelligentauthoring.valuerecommender.util.associationrules;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClient;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.util.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;

import java.io.IOException;
import java.util.List;

public class AssociationRulesManager {

  public static void generateRulesForTemplate(String templateId) throws IOException {

    ElasticsearchQueryService queryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig()
        .getElasticsearchConfig());

    CedarDataServices.initializeMongoClientFactoryForDocuments(ConfigManager.getCedarConfig().getTemplateServerConfig().getMongoConnection());
    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
    MongoConfig templateServerConfig = ConfigManager.getCedarConfig().getTemplateServerConfig();
    TemplateInstanceService<String, JsonNode> templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));

    List<String> templateInstancesIds = queryService.getTemplateInstancesIdsByTemplateId(templateId);

    for (String tiId : templateInstancesIds) {
      JsonNode templateInstance = templateInstanceService.findTemplateInstance(tiId);
      int a = 2;
    }

  }
}
