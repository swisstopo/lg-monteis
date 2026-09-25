package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the {@link KeycloakClaims} of a Keycloak-issued access token. Malformed claims degrade to
 * empty rather than throwing, so callers fail closed.
 */
final class KeycloakClaimExtractor {

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
    return jwt.getClaimAsString(KeycloakClaims.USERNAME);
  }

  List<String> roles() {
    // all or nothing: a malformed roles claim grants no role at all
    return clientAccessRoles().flatMap(this::allStrings).orElse(List.of());
  }

  List<UUID> readExperimentIds() {
    return experimentIds(KeycloakClaims.READ_EXPERIMENTS);
  }

  List<UUID> writeExperimentIds() {
    return experimentIds(KeycloakClaims.WRITE_EXPERIMENTS);
  }

  private List<UUID> experimentIds(String claim) {
    return claimAsList(claim).map(this::validUuids).orElse(List.of());
  }

  private Optional<List<?>> clientAccessRoles() {
    return Optional.ofNullable(jwt.getClaimAsMap(KeycloakClaims.CLIENT_ACCESS))
        .map(clientAccess -> clientAccess.get(KeycloakClaims.CLIENT_ACCESS_ROLES))
        .flatMap(this::asList);
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
