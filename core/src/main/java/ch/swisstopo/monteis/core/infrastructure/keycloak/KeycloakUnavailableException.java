package ch.swisstopo.monteis.core.infrastructure.keycloak;

public final class KeycloakUnavailableException extends KeycloakException {

  public KeycloakUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
