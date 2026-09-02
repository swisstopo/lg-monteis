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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds this app's {@link MonteisAuthenticationToken} from a JWT: client roles map to {@code
 * api:*} authorities, and sub/username/experiment_ids become the {@link MonteisPrincipal}.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  public static final String READ_AUTHORITY = "api:read";
  public static final String READ_ALL_AUTHORITY = "api:read-all";
  public static final String WRITE_AUTHORITY = "api:write";

  private static final String USERNAME_CLAIM = "preferred_username";
  private static final String CLIENT_ROLE_READ_ALL = "monteis-client:read-all";
  private static final String CLIENT_ROLE_WRITE = "monteis-client:write";
  private static final String CLIENT_ROLE_READ = "monteis-client:read";
  private static final String CLIENT_ACCESS_CLAIM = "monteis_access";
  private static final String CLIENT_ACCESS_CLAIM_NAME = "roles";
  private static final String EXPERIMENTS_CLAIM = "experiment_ids";
  private static final Set<String> READ_AUTHORITIES_SET =
      Set.of(READ_AUTHORITY, READ_ALL_AUTHORITY);

  // return built in OAuth2Error if keycloak users are misconfigured
  private static final OAuth2Error INVALID_ROLE_COMBINATION_ERROR =
      new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN,
          "monteis_access.roles contains an unsupported combination of client roles",
          null);

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {
    Collection<GrantedAuthority> authorities = extractAuthorities(source);

    // Fail closed: a caller with neither read authority must never leak a populated
    // experiment_ids claim through as if it were a legitimately scoped user.
    List<UUID> experimentIds = canReadAny(authorities) ? extractExperimentIds(source) : List.of();

    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.fromString(Objects.requireNonNull(source.getSubject())),
            source.getClaimAsString(USERNAME_CLAIM),
            experimentIds);

    return new MonteisAuthenticationToken(source, principal, authorities);
  }

  private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<String> roles = extractRoles(jwt);
    boolean hasRead = roles.contains(CLIENT_ROLE_READ);
    boolean hasReadAll = roles.contains(CLIENT_ROLE_READ_ALL);
    boolean hasWrite = roles.contains(CLIENT_ROLE_WRITE);

    if (hasWrite && !hasReadAll) {
      throw new OAuth2AuthenticationException(INVALID_ROLE_COMBINATION_ERROR);
    }

    Set<GrantedAuthority> authorities = new HashSet<>();
    if (hasReadAll) {
      // read-all subsumes read: a caller with both only ever needs the wider authority.
      authorities.add(new SimpleGrantedAuthority(READ_ALL_AUTHORITY));
      if (hasWrite) {
        authorities.add(new SimpleGrantedAuthority(WRITE_AUTHORITY));
      }
    } else if (hasRead) {
      authorities.add(new SimpleGrantedAuthority(READ_AUTHORITY));
    }

    return authorities;
  }

  private static boolean canReadAny(Collection<GrantedAuthority> authorities) {
    return authorities.stream().anyMatch(a -> READ_AUTHORITIES_SET.contains(a.getAuthority()));
  }

  private static List<String> extractRoles(Jwt jwt) {
    Map<String, Object> claimAccess = jwt.getClaimAsMap(CLIENT_ACCESS_CLAIM);
    if (claimAccess == null
        || !(claimAccess.get(CLIENT_ACCESS_CLAIM_NAME) instanceof List<?> rawRoles)) {
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

  private static List<UUID> extractExperimentIds(Jwt jwt) {
    List<?> experimentIds = jwt.getClaim(EXPERIMENTS_CLAIM);
    if (experimentIds == null) {
      return List.of();
    }
    return experimentIds.stream()
        .filter(String.class::isInstance)
        .map(id -> tryParseUuid((String) id))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private static UUID tryParseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
