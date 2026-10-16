package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

// ShedLock can only take a lock, not tell whether someone else holds it
public interface FulcrumSyncLockRepository {

  String LOCK_NAME = "fulcrum-document-sync";

  boolean isSyncRunning();
}
