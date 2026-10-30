package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

public interface HeldSyncLock extends AutoCloseable {

  @Override
  void close();
}
