package org.metadatacenter.intelligentauthoring.valuerecommender.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class RulesGenerationStatus {

  // Constants
  private final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  private final String TIMEZONE = "America/Los_Angeles";

  // Status enum
  public enum Status {PROCESSING, COMPLETED};

  // Attributes
  private final String templateId;
  private final int templateInstancesCount;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone=TIMEZONE)
  private final DateTime startTime;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone=TIMEZONE)
  private DateTime finishTime;
  private Long executionTimeMs;
  private Integer rulesIndexedCount;
  private Status status;

  public RulesGenerationStatus(String templateId, int templateInstancesCount, DateTime startTime,
                               DateTime finishTime, Long executionTimeMs, Integer rulesIndexedCount, Status status) {
    this.templateId = templateId;
    this.templateInstancesCount = templateInstancesCount;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.executionTimeMs = executionTimeMs;
    this.rulesIndexedCount = rulesIndexedCount;
    this.status = status;
  }

  public RulesGenerationStatus(String templateId, int templateInstancesCount, DateTime startTime, Status status) {
    this(templateId, templateInstancesCount, startTime, null, null, null, status);
  }

  public String getTemplateId() {
    return templateId;
  }

  public int getTemplateInstancesCount() {
    return templateInstancesCount;
  }

  public DateTime getStartTime() {
    return startTime;
  }

  public DateTime getFinishTime() {
    return finishTime;
  }

  public Long getExecutionTimeMs() {
    return executionTimeMs;
  }

  public Integer getRulesIndexedCount() {
    return rulesIndexedCount;
  }

  public Status getStatus() {
    return status;
  }

  public void setFinishTime(DateTime finishTime) {
    this.finishTime = finishTime;
  }

  public void setExecutionTime(Long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
  }

  public void setRulesIndexedCount(Integer rulesIndexedCount) {
    this.rulesIndexedCount = rulesIndexedCount;
  }

  public void setStatus(Status status) {
    this.status = status;
  }
}
