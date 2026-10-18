package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RulesGenerationStatusManager {

  // Generation threads and HTTP readers share this map. Keep transitions and snapshots under
  // one lock, and never expose the mutable entries to callers or asynchronous serialization.
  private static final Map<String, RulesGenerationStatus> statusMap = new HashMap<>();

  /**
   * The status of the latest rule generation for a template. A template no generation has run for is
   * refused as not found, since the request named something this server holds nothing about.
   */
  public static synchronized RulesGenerationStatus getStatus(String templateId) throws CedarProcessingException {
    RulesGenerationStatus status = statusMap.get(templateId);
    if (status == null) {
      CedarProcessingException absent =
          new CedarProcessingException("No rule generation has been recorded for the template " + templateId);
      absent.getErrorPack().status(CedarResponseStatus.NOT_FOUND);
      throw absent;
    }
    return copy(status);
  }

  public static synchronized List<RulesGenerationStatus> getStatus() {
    return statusMap.values().stream().map(RulesGenerationStatusManager::copy).toList();
  }

  public static synchronized void started(String templateId, int numberOfInstances) {
    RulesGenerationStatus status = new RulesGenerationStatus(templateId, numberOfInstances,
        Instant.now(), RulesGenerationStatus.Status.PROCESSING);
    status.setExecutionDuration(Duration.ZERO);
    statusMap.put(templateId, status);
  }

  public static synchronized void completed(String templateId, int rulesIndexedCount)
      throws CedarProcessingException {
    RulesGenerationStatus status = statusMap.get(templateId);
    if (status == null) throw new CedarProcessingException("Missing status for templateId: " + templateId);
    Instant finishTime = Instant.now();
    statusMap.put(templateId, new RulesGenerationStatus(templateId, status.getTemplateInstancesCount(),
        status.getStartTime(), finishTime, Duration.between(status.getStartTime(), finishTime),
        rulesIndexedCount, RulesGenerationStatus.Status.COMPLETED));
  }

  private static RulesGenerationStatus copy(RulesGenerationStatus status) {
    return new RulesGenerationStatus(status.getTemplateId(), status.getTemplateInstancesCount(),
        status.getStartTime(), status.getFinishTime(), status.getExecutionDuration(),
        status.getRulesIndexedCount(), status.getStatus());
  }
}
