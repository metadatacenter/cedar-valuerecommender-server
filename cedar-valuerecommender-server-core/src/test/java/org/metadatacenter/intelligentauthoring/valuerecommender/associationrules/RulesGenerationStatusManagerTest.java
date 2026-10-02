package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus.Status.*;

@Timeout(30)
class RulesGenerationStatusManagerTest {
  @Test void snapshotsRemainConsistentAcrossCompletionAndRestart() throws Exception {
    String id = UUID.randomUUID().toString();
    RulesGenerationStatusManager.started(id, 12);
    RulesGenerationStatus processing = RulesGenerationStatusManager.getStatus(id);
    List<RulesGenerationStatus> allProcessing = RulesGenerationStatusManager.getStatus();
    RulesGenerationStatusManager.completed(id, 7);
    RulesGenerationStatus completed = RulesGenerationStatusManager.getStatus(id);
    RulesGenerationStatusManager.started(id, 13);
    RulesGenerationStatus restarted = RulesGenerationStatusManager.getStatus(id);

    assertEquals(PROCESSING, processing.getStatus());
    assertNull(processing.getFinishTime());
    assertNull(processing.getRulesIndexedCount());
    var listed = allProcessing.stream().filter(status -> id.equals(status.getTemplateId())).findFirst().orElseThrow();
    assertEquals(PROCESSING, listed.getStatus(), "serializing a retained HTTP snapshot must not see a later transition");
    assertNull(listed.getFinishTime());
    assertEquals(COMPLETED, completed.getStatus());
    assertEquals(7, completed.getRulesIndexedCount());
    assertEquals(Duration.between(completed.getStartTime(), completed.getFinishTime()), completed.getExecutionDuration());
    assertEquals(PROCESSING, restarted.getStatus());
    assertEquals(13, restarted.getTemplateInstancesCount());
    assertNull(restarted.getRulesIndexedCount());
    assertNull(restarted.getFinishTime());
  }

  @Test void mutatingAReturnedStatusCannotChangeTheRegistry() throws Exception {
    String id = UUID.randomUUID().toString();
    RulesGenerationStatusManager.started(id, 2);
    RulesGenerationStatusManager.getStatus(id).setStatus(COMPLETED);
    RulesGenerationStatusManager.getStatus().stream().filter(status -> id.equals(status.getTemplateId()))
        .findFirst().orElseThrow().setRulesIndexedCount(99);
    assertEquals(PROCESSING, RulesGenerationStatusManager.getStatus(id).getStatus());
    assertNull(RulesGenerationStatusManager.getStatus(id).getRulesIndexedCount());
  }

  @Test void completingAnUnknownGenerationDoesNotCreateAnIncompleteStatus() {
    String id = UUID.randomUUID().toString();
    assertThrows(CedarProcessingException.class, () -> RulesGenerationStatusManager.completed(id, 1));
    assertThrows(CedarProcessingException.class, () -> RulesGenerationStatusManager.getStatus(id));
    assertTrue(RulesGenerationStatusManager.getStatus().stream().noneMatch(status -> id.equals(status.getTemplateId())));
  }

  @Test void concurrentRegistrationCompletionAndPollingNeverLoseEntriesOrExposeNulls() throws Exception {
    String prefix = UUID.randomUUID() + "/";
    var pool = Executors.newFixedThreadPool(5);
    var start = new CountDownLatch(1);
    List<Future<?>> writers = new ArrayList<>();
    try {
      for (int writer = 0; writer < 4; writer++) {
        int lane = writer;
        writers.add(pool.submit(() -> {
          start.await();
          for (int i = 0; i < 150; i++) {
            String id = prefix + lane + "/" + i;
            RulesGenerationStatusManager.started(id, i);
            RulesGenerationStatusManager.completed(id, i);
          }
          return null;
        }));
      }
      Future<?> reader = pool.submit(() -> {
        start.await();
        do {
          for (RulesGenerationStatus status : RulesGenerationStatusManager.getStatus()) {
            assertNotNull(status);
            assertNotNull(status.getTemplateId());
            assertNotNull(status.getStartTime());
            if (status.getStatus() == COMPLETED) {
              assertNotNull(status.getFinishTime());
              assertNotNull(status.getRulesIndexedCount());
            } else {
              assertEquals(PROCESSING, status.getStatus());
              assertNull(status.getFinishTime());
              assertNull(status.getRulesIndexedCount());
            }
          }
        } while (writers.stream().anyMatch(writer -> !writer.isDone()));
        return null;
      });
      start.countDown();
      for (Future<?> writer : writers) writer.get(15, TimeUnit.SECONDS);
      reader.get(15, TimeUnit.SECONDS);
      List<RulesGenerationStatus> result = RulesGenerationStatusManager.getStatus().stream()
          .filter(status -> status.getTemplateId().startsWith(prefix)).toList();
      assertEquals(600, result.size(), "concurrent HashMap growth must not lose a registered generation");
      assertEquals(600, result.stream().map(RulesGenerationStatus::getTemplateId).distinct().count());
      assertTrue(result.stream().allMatch(status -> status.getStatus() == COMPLETED));
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }
}
