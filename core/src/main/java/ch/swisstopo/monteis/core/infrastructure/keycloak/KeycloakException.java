package ch.swisstopo.monteis.core.infrastructure.keycloak;

/** A call to the Keycloak admin API did not give an answer to work with. */
public abstract sealed class KeycloakException extends RuntimeException
    permits KeycloakAccessDeniedException, KeycloakUnavailableException {

  KeycloakException(String message, Throwable cause) {
    super(message, cause);
  }
}
