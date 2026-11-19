package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The only facade from a Keycloak access token to this app's {@link MonteisAuthenticationToken}:
 * knows the claim names, the monteis-client role names, the authority names and their implications
 * (ADR-013). Malformed claims degrade to empty rather than throwing, so access fails closed.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String EXPERIMENT_READ = "experiment:read";
  static final String EXPERIMENT_WRITE = "experiment:write";
  static final String EXPERIMENT_READ_ALL = "experiment:read:all";
  static final String EXPERIMENT_WRITE_ALL = "experiment:write:all";

  private static final String MONTEIS_CLIENT = "monteis-client:";
  private static final Map<String, Set<String>> AUTHORITIES_BY_ROLE =
      Map.of(
          MONTEIS_CLIENT + "experiment:read",
          Set.of(EXPERIMENT_READ),
          MONTEIS_CLIENT + "experiment:write",
          Set.of(EXPERIMENT_READ, EXPERIMENT_WRITE),
          MONTEIS_CLIENT + "admin",
          Set.of(EXPERIMENT_READ_ALL, EXPERIMENT_WRITE_ALL));

  private static final String USERNAME = "preferred_username";
  private static final String CLIENT_ACCESS = "monteis_access";
  private static final String CLIENT_ACCESS_ROLES = "roles";
  private static final String READ_EXPERIMENT_IDS = "read_experiment_ids";
  private static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {
    Set<String> authorities = authoritiesOf(rolesOf(source));
    Set<UUID> writeExperimentIds = writeExperimentIdsOf(source, authorities);
    return new MonteisAuthenticationToken(
        source,
        principalOf(source),
        authorities.stream().map(SimpleGrantedAuthority::new).toList(),
        readExperimentIdsOf(source, authorities, writeExperimentIds),
        writeExperimentIds);
  }

  private static List<String> rolesOf(Jwt jwt) {
    // all or nothing: a malformed roles claim grants no role at all
    return clientAccessRolesOf(jwt)
        .flatMap(MonteisJwtAuthenticationConverter::allStrings)
        .orElse(List.of());
  }

  private static Set<String> authoritiesOf(List<String> roles) {
    return roles.stream()
        .map(role -> AUTHORITIES_BY_ROLE.getOrDefault(role, Set.of()))
        .flatMap(Set::stream)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Set<UUID> writeExperimentIdsOf(Jwt jwt, Set<String> authorities) {
    if (authorities.contains(EXPERIMENT_WRITE_ALL) || !authorities.contains(EXPERIMENT_WRITE)) {
      return Set.of();
    }
    return experimentIdsOf(jwt, WRITE_EXPERIMENT_IDS);
  }

  private static Set<UUID> readExperimentIdsOf(
      Jwt jwt, Set<String> authorities, Set<UUID> writeExperimentIds) {
    if (authorities.contains(EXPERIMENT_READ_ALL) || !authorities.contains(EXPERIMENT_READ)) {
      return Set.of();
    }
    return Stream.concat(
            experimentIdsOf(jwt, READ_EXPERIMENT_IDS).stream(), writeExperimentIds.stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static MonteisPrincipal principalOf(Jwt jwt) {
    return new MonteisPrincipal(
        UUID.fromString(Objects.requireNonNull(jwt.getSubject())), jwt.getClaimAsString(USERNAME));
  }

  private static Optional<List<?>> clientAccessRolesOf(Jwt jwt) {
    // not getClaimAsMap: it throws for a claim that isn't an object instead of degrading to empty
    return Optional.ofNullable(jwt.getClaim(CLIENT_ACCESS))
        .flatMap(MonteisJwtAuthenticationConverter::asMap)
        .map(clientAccess -> clientAccess.get(CLIENT_ACCESS_ROLES))
        .flatMap(MonteisJwtAuthenticationConverter::asList);
  }

  private static Set<UUID> experimentIdsOf(Jwt jwt, String claim) {
    return Optional.ofNullable(jwt.getClaim(claim))
        .flatMap(MonteisJwtAuthenticationConverter::asList)
        .map(MonteisJwtAuthenticationConverter::validUuids)
        .orElse(Set.of());
  }

  private static Optional<Map<?, ?>> asMap(Object claim) {
    return claim instanceof Map<?, ?> map ? Optional.of(map) : Optional.empty();
  }

  private static Optional<List<?>> asList(Object claim) {
    return claim instanceof List<?> list ? Optional.of(list) : Optional.empty();
  }

  private static Optional<List<String>> allStrings(List<?> values) {
    return values.stream().allMatch(String.class::isInstance)
        ? Optional.of(strings(values))
        : Optional.empty();
  }

  // lenient, unlike allStrings: entries that aren't UUID strings are dropped one by one
  private static Set<UUID> validUuids(List<?> values) {
    return strings(values).stream()
        .map(MonteisJwtAuthenticationConverter::tryParseUuid)
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static List<String> strings(List<?> values) {
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  private static Optional<UUID> tryParseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException _) {
      return Optional.empty();
    }
  }
}
