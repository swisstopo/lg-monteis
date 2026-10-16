package ch.swisstopo.monteis.core.infrastructure.fulcrum;

public class FulcrumSyncAlreadyRunningException extends RuntimeException {

  public static final String MESSAGE_KEY = "fulcrum.sync.alreadyRunning";

  public FulcrumSyncAlreadyRunningException() {
    super("A Fulcrum Document sync is already running");
  }
}
