package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.joda.time.DateTime;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.intelligentauthoring.valuerecommender.io.RulesGenerationStatus;
import org.metadatacenter.intelligentauthoring.valuerecommender.io.RulesGenerationStatus.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RulesGenerationStatusManager {

  private static final Logger logger = LoggerFactory.getLogger(RulesGenerationStatusManager.class);
  private static ElasticsearchQueryService esQueryService;
  private static HashMap<String, RulesGenerationStatus> statusMap;

  static {
    try {
      statusMap = new HashMap<>();
      esQueryService = new ElasticsearchQueryService(ConfigManager.getCedarConfig().getElasticsearchConfig());
    } catch (UnknownHostException e) {
      logger.error(e.getMessage());
      e.printStackTrace();
    }
  }

  public static RulesGenerationStatus getStatus(String templateId) throws CedarProcessingException {
    if (statusMap.containsKey(templateId)) {
      return statusMap.get(templateId);
    }
    else {
      throw new CedarProcessingException("Template not found: " + templateId);
    }
  }

  public static List<RulesGenerationStatus> getStatus() {
    return new ArrayList<> (statusMap.values());
  }

  public static void setStatus(String templateId, Status newStatus) throws CedarProcessingException {
    setStatus(templateId, newStatus, null);
  }

  public static void setStatus(String templateId, Status newStatus, Integer rulesIndexedCount) throws CedarProcessingException {
    if (newStatus.equals(Status.PROCESSING)) {
      int numberOfInstances = esQueryService.getTemplateInstancesIdsByTemplateId(templateId).size();
      DateTime startTime = new DateTime(System.currentTimeMillis());
      RulesGenerationStatus rgs = new RulesGenerationStatus(templateId, numberOfInstances, startTime, newStatus);
      rgs.setExecutionTime(System.currentTimeMillis() - rgs.getStartTime().getMillis());
      statusMap.put(templateId, rgs);
    }
    else if (newStatus.equals(Status.COMPLETED)) {
      if (statusMap.containsKey(templateId)) {
        RulesGenerationStatus rgs = statusMap.get(templateId);
        rgs.setFinishTime(new DateTime(System.currentTimeMillis()));
        rgs.setExecutionTime(rgs.getFinishTime().getMillis() - rgs.getStartTime().getMillis());
        rgs.setRulesIndexedCount(rulesIndexedCount);
        rgs.setStatus(newStatus);
        statusMap.put(templateId, rgs);
      }
      else {
        logger.error("Missing status for templateId: " + templateId);
        throw new CedarProcessingException("Missing status for templateId: " + templateId);
      }
    }
    else {
      logger.warn("Cannot set status: " + newStatus.name());
    }
  }
}
