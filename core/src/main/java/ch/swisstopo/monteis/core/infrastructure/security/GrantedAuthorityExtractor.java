package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.DOCUMENTS_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/** Maps the Keycloak client roles of a caller 1:1 to this app's {@code api:*} authorities. */
class GrantedAuthorityExtractor {

  private static final Map<String, String> AUTHORITY_BY_ROLE =
      Map.of(
          KeycloakClientRoles.EXPERIMENT_READ, EXPERIMENT_READ_AUTHORITY,
          KeycloakClientRoles.EXPERIMENT_READ_ALL, EXPERIMENT_READ_ALL_AUTHORITY,
          KeycloakClientRoles.EXPERIMENT_WRITE, EXPERIMENT_WRITE_AUTHORITY,
          KeycloakClientRoles.EXPERIMENT_WRITE_ALL, EXPERIMENT_WRITE_ALL_AUTHORITY,
          KeycloakClientRoles.DOCUMENTS_READ, DOCUMENTS_READ_AUTHORITY,
          KeycloakClientRoles.ADMIN, ADMIN_AUTHORITY);

  // return built in OAuth2Error if keycloak users are misconfigured
  private static final OAuth2Error INVALID_ROLE_COMBINATION_ERROR =
      new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN,
          "monteis_access.roles contains an unsupported combination of client roles",
          null);

  /**
   * Returns the authorities for {@code roles}; roles unknown to this app are ignored.
   *
   * @throws OAuth2AuthenticationException if a write role comes without the read role of the same
   *     scope
   */
  Set<GrantedAuthority> extract(List<String> roles) {
    // write implies read at the same scope: the Keycloak groups always grant both together
    boolean writeWithoutRead =
        roles.contains(KeycloakClientRoles.EXPERIMENT_WRITE)
            && !roles.contains(KeycloakClientRoles.EXPERIMENT_READ);
    boolean writeAllWithoutReadAll =
        roles.contains(KeycloakClientRoles.EXPERIMENT_WRITE_ALL)
            && !roles.contains(KeycloakClientRoles.EXPERIMENT_READ_ALL);
    if (writeWithoutRead || writeAllWithoutReadAll) {
      throw new OAuth2AuthenticationException(INVALID_ROLE_COMBINATION_ERROR);
    }

    return roles.stream()
        .map(AUTHORITY_BY_ROLE::get)
        .filter(Objects::nonNull)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toSet());
  }
}
