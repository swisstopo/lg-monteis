package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import ch.swisstopo.monteis.core.infrastructure.security.Permissions;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The four MON-196 privilege levels, defined once for every test: their Keycloak client roles (as
 * the realm groups grant them), the {@code api:*} authorities those map to, and the experiment ids
 * a token of that level carries.
 *
 * <p>Scoped levels are assigned {@link #ASSIGNED_EXPERIMENT} ({@code ExperimentPI} with write);
 * {@link #OTHER_EXPERIMENT} is readable but not writable for them; {@link #UNASSIGNED_EXPERIMENT}
 * is assigned to nobody.
 */
public enum PrivilegeLevel {
  BASISROLLE(List.of(), List.of(), List.of(), List.of()),
  EXPERIMENT_USER(
      List.of("monteis-client:experiment:read"),
      List.of(Permissions.EXPERIMENT_READ),
      List.of(SeedData.EXPERIMENT_ALPHA, SeedData.EXPERIMENT_GAMMA),
      List.of()),
  EXPERIMENT_PI(
      List.of("monteis-client:experiment:read", "monteis-client:experiment:write"),
      List.of(Permissions.EXPERIMENT_READ, Permissions.EXPERIMENT_WRITE),
      List.of(SeedData.EXPERIMENT_GAMMA),
      List.of(SeedData.EXPERIMENT_ALPHA)),
  MONTEIS_ADMIN(
      List.of("monteis-client:admin"), List.of(Permissions.WRITE_ALL), List.of(), List.of());

  public static final UUID ASSIGNED_EXPERIMENT = SeedData.EXPERIMENT_ALPHA;
  public static final UUID OTHER_EXPERIMENT = SeedData.EXPERIMENT_GAMMA;
  public static final UUID UNASSIGNED_EXPERIMENT = SeedData.EXPERIMENT_BETA;

  private final List<String> roles;
  private final List<String> authorities;
  private final List<UUID> readExperimentIds;
  private final List<UUID> writeExperimentIds;

  PrivilegeLevel(
      List<String> roles,
      List<String> authorities,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds) {
    this.roles = roles;
    this.authorities = authorities;
    this.readExperimentIds = readExperimentIds;
    this.writeExperimentIds = writeExperimentIds;
  }

  /** The {@code monteis_access.roles} a token of this level carries. */
  public List<String> roles() {
    return roles;
  }

  public List<GrantedAuthority> grantedAuthorities() {
    return authorities.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
  }

  public List<UUID> readExperimentIds() {
    return readExperimentIds;
  }

  public List<UUID> writeExperimentIds() {
    return writeExperimentIds;
  }

  public Set<UUID> readableExperimentIds() {
    return Stream.concat(readExperimentIds.stream(), writeExperimentIds.stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The authentication {@code MonteisJwtAuthenticationConverter} produces for this level. */
  public MonteisAuthenticationToken authentication() {
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), name().toLowerCase());
    return new MonteisAuthenticationToken(
        null, principal, grantedAuthorities(), readExperimentIds(), writeExperimentIds());
  }

  /** A decoded access token of this level, as the resource server hands it to the converter. */
  public Jwt jwt(String tokenValue) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", name().toLowerCase());
    claims.put("monteis_access", Map.of("roles", roles));
    claims.put("read_experiment_ids", readExperimentIds().stream().map(UUID::toString).toList());
    claims.put("write_experiment_ids", writeExperimentIds().stream().map(UUID::toString).toList());
    Instant now = Instant.now();
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "none")
        .claims(c -> c.putAll(claims))
        .subject(UUID.randomUUID().toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build();
  }
}
