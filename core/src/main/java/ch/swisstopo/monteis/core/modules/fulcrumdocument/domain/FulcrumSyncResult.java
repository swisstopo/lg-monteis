package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

public record FulcrumSyncResult(
    FulcrumSyncStatus status, int documentsSeen, int filesStored, int filesFailed) {

  public static FulcrumSyncResult failed() {
    return new FulcrumSyncResult(FulcrumSyncStatus.FAILED, 0, 0, 0);
  }
}
