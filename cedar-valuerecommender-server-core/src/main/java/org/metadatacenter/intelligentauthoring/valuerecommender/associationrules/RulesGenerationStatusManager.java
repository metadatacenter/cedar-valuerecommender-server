package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.ConfigManager;
import org.metadatacenter.intelligentauthoring.valuerecommender.elasticsearch.ElasticsearchQueryService;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
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
    } else {
      throw new CedarProcessingException("Template not found: " + templateId);
    }
  }

  public static List<RulesGenerationStatus> getStatus() {
    return new ArrayList<>(statusMap.values());
  }

  public static void setStatus(String templateId, RulesGenerationStatus.Status newStatus)
      throws CedarProcessingException {
    setStatus(templateId, newStatus, null);
  }

  public static void setStatus(String templateId, RulesGenerationStatus.Status newStatus, Integer rulesIndexedCount)
      throws CedarProcessingException {
    if (newStatus.equals(RulesGenerationStatus.Status.PROCESSING)) {
      int numberOfInstances = esQueryService.getTemplateInstancesIdsByTemplateId(templateId).size();
      Instant startTime = Instant.now();
      RulesGenerationStatus rgs = new RulesGenerationStatus(templateId, numberOfInstances, startTime, newStatus);
      rgs.setExecutionDuration(Duration.between(startTime, Instant.now()));
      statusMap.put(templateId, rgs);
    } else if (newStatus.equals(RulesGenerationStatus.Status.COMPLETED)) {
      if (statusMap.containsKey(templateId)) {
        RulesGenerationStatus rgs = statusMap.get(templateId);
        rgs.setFinishTime(Instant.now());
        rgs.setExecutionDuration(Duration.between(rgs.getStartTime(), rgs.getFinishTime()));
        rgs.setRulesIndexedCount(rulesIndexedCount);
        rgs.setStatus(newStatus);
        statusMap.put(templateId, rgs);
      } else {
        logger.error("Missing status for templateId: " + templateId);
        throw new CedarProcessingException("Missing status for templateId: " + templateId);
      }
    } else {
      logger.warn("Cannot set status: " + newStatus.name());
    }
  }
}
