package ch.swisstopo.monteis.core.infrastructure.keycloak;

/** Keycloak answered 403: the caller's token may not do what was asked. */
public final class KeycloakAccessDeniedException extends KeycloakException {

  public KeycloakAccessDeniedException(String message, Throwable cause) {
    super(message, cause);
  }
}
