package ch.swisstopo.monteis.core.infrastructure.fulcrum;

public class FulcrumDisabledException extends RuntimeException {

  public static final String MESSAGE_KEY = "fulcrum.disabled";

  public FulcrumDisabledException() {
    super("Fulcrum is switched off (monteis.fulcrum.enabled=false)");
  }
}
