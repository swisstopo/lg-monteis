package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

public enum FulcrumSyncStatus {
  RUNNING,
  SUCCEEDED,
  // the metadata is up to date, but some files are still missing: out of time or a download failed
  INCOMPLETE,
  FAILED
}
