package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the claims of a Keycloak-issued access token, the only class that knows their names and the
 * monteis-client role names (contract C1). Malformed claims degrade to empty rather than throwing,
 * so callers fail closed.
 */
final class KeycloakClaimExtractor {

  private static final String MONTEIS_CLIENT = "monteis-client:";
  static final String EXPERIMENT_READ_ROLE = MONTEIS_CLIENT + "experiment:read";
  static final String EXPERIMENT_WRITE_ROLE = MONTEIS_CLIENT + "experiment:write";
  static final String ADMIN_ROLE = MONTEIS_CLIENT + "admin";

  private static final String USERNAME = "preferred_username";
  private static final String CLIENT_ACCESS = "monteis_access";
  private static final String CLIENT_ACCESS_ROLES = "roles";
  private static final String READ_EXPERIMENTS = "read_experiment_ids";
  private static final String WRITE_EXPERIMENTS = "write_experiment_ids";

  private final Jwt jwt;

  private KeycloakClaimExtractor(Jwt jwt) {
    this.jwt = jwt;
  }

  static KeycloakClaimExtractor from(Jwt jwt) {
    return new KeycloakClaimExtractor(jwt);
  }

  UUID subject() {
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
  }

  String username() {
    return jwt.getClaimAsString(USERNAME);
  }

  List<String> roles() {
    // all or nothing: a malformed roles claim grants no role at all
    return clientAccessRoles().flatMap(this::allStrings).orElse(List.of());
  }

  List<UUID> readExperimentIds() {
    return experimentIds(READ_EXPERIMENTS);
  }

  List<UUID> writeExperimentIds() {
    return experimentIds(WRITE_EXPERIMENTS);
  }

  private List<UUID> experimentIds(String claim) {
    return claimAsList(claim).map(this::validUuids).orElse(List.of());
  }

  private Optional<List<?>> clientAccessRoles() {
    // not getClaimAsMap: it throws for a claim that isn't an object instead of degrading to empty
    return Optional.ofNullable(jwt.getClaim(CLIENT_ACCESS))
        .flatMap(this::asMap)
        .map(clientAccess -> clientAccess.get(CLIENT_ACCESS_ROLES))
        .flatMap(this::asList);
  }

  private Optional<Map<?, ?>> asMap(Object claim) {
    return claim instanceof Map<?, ?> map ? Optional.of(map) : Optional.empty();
  }

  private Optional<List<?>> claimAsList(String claim) {
    return Optional.ofNullable(jwt.getClaim(claim)).flatMap(this::asList);
  }

  private Optional<List<?>> asList(Object claim) {
    return claim instanceof List<?> list ? Optional.of(list) : Optional.empty();
  }

  private Optional<List<String>> allStrings(List<?> values) {
    return values.stream().allMatch(String.class::isInstance)
        ? Optional.of(strings(values))
        : Optional.empty();
  }

  // lenient, unlike allStrings: entries that aren't UUID strings are dropped one by one
  private List<UUID> validUuids(List<?> values) {
    return strings(values).stream()
        .map(Uuids::tryParse)
        .flatMap(Optional::stream)
        .distinct()
        .toList();
  }

  private List<String> strings(List<?> values) {
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }
}
