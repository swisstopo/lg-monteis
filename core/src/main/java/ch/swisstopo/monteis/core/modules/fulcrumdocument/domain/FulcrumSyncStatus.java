package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

public enum FulcrumSyncStatus {
  RUNNING,
  SUCCEEDED,
  // the entries are up to date, but some documents are still missing: out of time or a download
  // failed
  INCOMPLETE,
  FAILED
}
