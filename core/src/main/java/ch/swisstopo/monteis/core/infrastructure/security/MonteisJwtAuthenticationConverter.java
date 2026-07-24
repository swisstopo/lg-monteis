package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds this app's {@link MonteisAuthenticationToken} from a JWT: realm roles map to {@code
 * api:*} authorities, and sub/username/experiment_ids become the {@link MonteisPrincipal}.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  public static final String READ_AUTHORITY = "api:read";
  public static final String READ_ALL_AUTHORITY = "api:read-all";
  public static final String WRITE_AUTHORITY = "api:write";

  private static final String USERNAME_CLAIM = "preferred_username";
  private static final String ADMIN_ROLE = "admin";
  private static final String USER_ROLE = "user";
  private static final String REALM_ACCESS = "realm_access";
  private static final String ROLE_CLAIM = "roles";
  private static final String EXPERIMENTS_CLAIM = "experiment_ids";
  private static final Set<String> READ_AUTHORITIES_SET =
      Set.of(READ_AUTHORITY, READ_ALL_AUTHORITY);

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {

    Collection<GrantedAuthority> authorities = extractAuthorities(source);

    // Fail closed: a caller with neither read authority must never leak a populated
    // experiment_ids claim through as if it were a legitimately scoped user.
    boolean hasAnyReadAuthority = hasAuthority(authorities);
    List<Long> experimentIds = hasAnyReadAuthority ? extractExperimentIds(source) : List.of();

    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.fromString(Objects.requireNonNull(source.getSubject())),
            source.getClaimAsString(USERNAME_CLAIM),
            experimentIds);

    return new MonteisAuthenticationToken(source, principal, authorities);
  }

  @SuppressWarnings("java:S1301")
  private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Set<GrantedAuthority> authorities = new HashSet<>();
    for (String role : extractRoles(jwt)) {
      switch (role) {
        case USER_ROLE -> authorities.add(new SimpleGrantedAuthority(READ_AUTHORITY));

        case ADMIN_ROLE -> {
          authorities.add(new SimpleGrantedAuthority(WRITE_AUTHORITY));
          authorities.add(new SimpleGrantedAuthority(READ_ALL_AUTHORITY));
        }
        default -> {
          /* Ignore unknown roles */
        }
      }
    }

    return authorities;
  }

  private static boolean hasAuthority(Collection<GrantedAuthority> authorities) {
    return authorities.stream().anyMatch(a -> READ_AUTHORITIES_SET.contains(a.getAuthority()));
  }

  private static List<String> extractRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
    if (realmAccess == null || !(realmAccess.get(ROLE_CLAIM) instanceof List<?> rawRoles)) {
      return List.of();
    }
    List<String> roles = new ArrayList<>(rawRoles.size());
    for (Object role : rawRoles) {
      if (!(role instanceof String roleName)) {
        return List.of();
      }
      roles.add(roleName);
    }
    return roles;
  }

  private static List<Long> extractExperimentIds(Jwt jwt) {
    List<?> experimentIds = jwt.getClaim(EXPERIMENTS_CLAIM);
    if (experimentIds == null) {
      return List.of();
    }
    return experimentIds.stream()
        .filter(Number.class::isInstance)
        .map(id -> ((Number) id).longValue())
        .distinct()
        .toList();
  }
}
