package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FulcrumDocumentSyncJob {

  private final FulcrumDocumentSyncRunner syncRunner;

  public FulcrumDocumentSyncJob(FulcrumDocumentSyncRunner syncRunner) {
    this.syncRunner = syncRunner;
  }

  @Scheduled(cron = "${monteis.fulcrum.documents.sync-cron}", zone = "Europe/Zurich")
  public void syncUnlessRunning() {
    syncRunner.runUnlessRunning();
  }
}
