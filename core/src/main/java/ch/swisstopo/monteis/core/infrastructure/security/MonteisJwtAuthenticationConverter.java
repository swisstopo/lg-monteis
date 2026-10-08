package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The one place that turns a Keycloak access token into this app's {@link
 * MonteisAuthenticationToken}: the client roles read by {@link KeycloakClaimExtractor} map 1:1 to
 * {@link Permissions} (BR4.1), roles unknown to this app are ignored, and the identity and
 * experiment ids become the {@link MonteisPrincipal}.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final Logger log =
      LoggerFactory.getLogger(MonteisJwtAuthenticationConverter.class);

  private static final Map<String, String> PERMISSION_BY_ROLE =
      Map.of(
          KeycloakClaimExtractor.EXPERIMENT_READ_ROLE, Permissions.EXPERIMENT_READ,
          KeycloakClaimExtractor.EXPERIMENT_WRITE_ROLE, Permissions.EXPERIMENT_WRITE,
          KeycloakClaimExtractor.ADMIN_ROLE, Permissions.WRITE_ALL);

  // return built in OAuth2Error if keycloak users are misconfigured
  private static final OAuth2Error INVALID_ROLE_COMBINATION_ERROR =
      new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN,
          "monteis_access.roles contains an unsupported combination of client roles",
          null);

  /**
   * @throws OAuth2AuthenticationException if {@code experiment:write} comes without {@code
   *     experiment:read} (BR4.2)
   */
  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {
    KeycloakClaimExtractor claims = KeycloakClaimExtractor.from(source);
    UUID subject = claims.subject();
    Set<String> permissions = permissionsOf(subject, claims.roles());

    // Fail closed: a caller without the matching permission must never leak a populated
    // experiment id claim through as if it were a legitimately scoped user.
    List<UUID> readExperimentIds =
        permissions.contains(Permissions.EXPERIMENT_READ) ? claims.readExperimentIds() : List.of();
    // MonteisPrincipal drops write ids outside the read ids
    List<UUID> writeExperimentIds =
        permissions.contains(Permissions.EXPERIMENT_WRITE)
            ? claims.writeExperimentIds()
            : List.of();

    MonteisPrincipal principal =
        new MonteisPrincipal(subject, claims.username(), readExperimentIds, writeExperimentIds);
    List<GrantedAuthority> authorities =
        permissions.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();

    return new MonteisAuthenticationToken(source, principal, authorities);
  }

  private static Set<String> permissionsOf(UUID subject, List<String> roles) {
    // the Keycloak write groups always grant read too, so write alone is a misconfiguration
    if (roles.contains(KeycloakClaimExtractor.EXPERIMENT_WRITE_ROLE)
        && !roles.contains(KeycloakClaimExtractor.EXPERIMENT_READ_ROLE)) {
      log.warn(
          "Rejected token of sub {}: role {} requires role {}",
          subject,
          KeycloakClaimExtractor.EXPERIMENT_WRITE_ROLE,
          KeycloakClaimExtractor.EXPERIMENT_READ_ROLE);
      throw new OAuth2AuthenticationException(INVALID_ROLE_COMBINATION_ERROR);
    }
    return roles.stream()
        .map(PERMISSION_BY_ROLE::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }
}
