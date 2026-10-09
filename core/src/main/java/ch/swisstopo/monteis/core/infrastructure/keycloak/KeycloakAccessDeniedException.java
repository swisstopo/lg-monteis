package ch.swisstopo.monteis.core.infrastructure.keycloak;

public final class KeycloakAccessDeniedException extends KeycloakException {

  public KeycloakAccessDeniedException(String message, Throwable cause) {
    super(message, cause);
  }
}
