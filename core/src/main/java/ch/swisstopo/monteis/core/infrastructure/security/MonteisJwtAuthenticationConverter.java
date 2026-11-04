package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The one place that turns a Keycloak access token into this app's {@link
 * MonteisAuthenticationToken}: the client roles read by {@link KeycloakClaimExtractor} map 1:1 to
 * {@link Permissions} (BR4.1), roles unknown to this app are ignored, and the identity and
 * experiment ids become the {@link MonteisPrincipal}.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final Map<String, Set<String>> PERMISSIONS_BY_ROLE =
      Map.of(
          KeycloakClaimExtractor.EXPERIMENT_READ_ROLE,
          Set.of(Permissions.EXPERIMENT_READ),
          KeycloakClaimExtractor.EXPERIMENT_WRITE_ROLE,
          Set.of(Permissions.EXPERIMENT_READ, Permissions.EXPERIMENT_WRITE),
          KeycloakClaimExtractor.ADMIN_ROLE,
          Set.of(Permissions.ADMIN));

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {
    KeycloakClaimExtractor claims = KeycloakClaimExtractor.from(source);
    MonteisPrincipal principal = new MonteisPrincipal(claims.subject(), claims.username());
    return new MonteisAuthenticationToken(
        source,
        principal,
        authoritiesOf(claims.roles()),
        claims.readExperimentIds(),
        claims.writeExperimentIds());
  }

  private static List<GrantedAuthority> authoritiesOf(List<String> roles) {
    return roles.stream()
        .map(role -> PERMISSIONS_BY_ROLE.getOrDefault(role, Set.of()))
        .flatMap(Set::stream)
        .distinct()
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .toList();
  }
}
