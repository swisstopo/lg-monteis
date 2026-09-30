package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Maps the Keycloak client roles of a caller 1:1 to this app's {@link Grant}s (BR4.1).
 * Roles unknown to this app, including the removed legacy ones, are ignored.
 */
class GrantedAuthorityExtractor {

  private static final Logger log = LoggerFactory.getLogger(GrantedAuthorityExtractor.class);

  private static final Map<String, Grant> GRANT_BY_ROLE =
      Map.of(
          KeycloakClientRoles.EXPERIMENT_READ, Grant.EXPERIMENT_READ,
          KeycloakClientRoles.EXPERIMENT_WRITE, Grant.EXPERIMENT_WRITE,
          KeycloakClientRoles.EXPERIMENT_WRITE_ALL, Grant.EXPERIMENT_WRITE_ALL,
          KeycloakClientRoles.DOCUMENTS_READ, Grant.DOCUMENTS_READ,
          KeycloakClientRoles.ADMIN, Grant.ADMIN);

  // return built in OAuth2Error if keycloak users are misconfigured
  private static final OAuth2Error INVALID_ROLE_COMBINATION_ERROR =
      new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN,
          "monteis_access.roles contains an unsupported combination of client roles",
          null);

  /**
   * Returns the grants for {@code roles}; roles unknown to this app are ignored.
   *
   * @param subject the token's {@code sub}, used only to log a rejection
   * @throws OAuth2AuthenticationException if {@code experiment:write} comes without {@code
   *     experiment:read} (BR4.2); {@code experiment:write:all} alone is valid, it implies read
   */
  Set<GrantedAuthority> extract(UUID subject, List<String> roles) {
    // the Keycloak write groups always grant read too, so write alone is a misconfiguration
    if (roles.contains(KeycloakClientRoles.EXPERIMENT_WRITE)
        && !roles.contains(KeycloakClientRoles.EXPERIMENT_READ)) {
      log.warn(
          "Rejected token of sub {}: role {} requires role {}",
          subject,
          KeycloakClientRoles.EXPERIMENT_WRITE,
          KeycloakClientRoles.EXPERIMENT_READ);
      throw new OAuth2AuthenticationException(INVALID_ROLE_COMBINATION_ERROR);
    }

    return roles.stream()
        .map(GRANT_BY_ROLE::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }
}
