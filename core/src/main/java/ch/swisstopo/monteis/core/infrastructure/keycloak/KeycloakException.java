package ch.swisstopo.monteis.core.infrastructure.keycloak;

public abstract sealed class KeycloakException extends RuntimeException
    permits KeycloakAccessDeniedException, KeycloakUnavailableException {

  KeycloakException(String message, Throwable cause) {
    super(message, cause);
  }
}
