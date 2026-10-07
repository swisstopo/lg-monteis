package ch.swisstopo.monteis.core.infrastructure.keycloak;

/** Keycloak could not be asked: no caller token, a transport error, a timeout or a 5xx. */
public final class KeycloakUnavailableException extends KeycloakException {

  public KeycloakUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
