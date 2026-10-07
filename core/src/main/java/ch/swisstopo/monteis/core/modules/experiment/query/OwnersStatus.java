package ch.swisstopo.monteis.core.modules.experiment.query;

/** Whether the visible owners are the real ones, and if not, why they are missing. */
public enum OwnersStatus {
  /** Keycloak knows the PIs, the owners shown are all there are. */
  SHOWN,
  /** Keycloak could not be asked, the owners are unknown right now. */
  KEYCLOAK_UNAVAILABLE,
  /** Keycloak refused to show the PIs, its permissions for MONTEIS are misconfigured. */
  ACCESS_DENIED,
  /** The experiment has owners but no write group in Keycloak, so no one is a PI. */
  NO_WRITE_GROUP
}
