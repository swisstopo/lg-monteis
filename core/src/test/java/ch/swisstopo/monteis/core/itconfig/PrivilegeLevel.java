package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.Grant;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The five MON-196 privilege levels, defined once for every test: their Keycloak client roles (as
 * the realm groups grant them), the {@code api:*} authorities those map to, and the experiment ids
 * a token of that level carries.
 *
 * <p>Scoped levels are assigned {@link #ASSIGNED_EXPERIMENT} ({@code ExperimentPI} with write);
 * {@link #OTHER_EXPERIMENT} is readable but not writable for them; {@link #UNASSIGNED_EXPERIMENT}
 * is assigned to nobody.
 */
public enum PrivilegeLevel {
  BASISROLLE(List.of(), List.of(), false, false),
  EXPERIMENT_USER(
      List.of("monteis-client:experiment:read", "monteis-client:documents:read"),
      List.of(Grant.EXPERIMENT_READ, Grant.DOCUMENTS_READ),
      true,
      false),
  EXPERIMENT_PI(
      List.of(
          "monteis-client:experiment:read",
          "monteis-client:experiment:write",
          "monteis-client:documents:read"),
      List.of(Grant.EXPERIMENT_READ, Grant.EXPERIMENT_WRITE, Grant.DOCUMENTS_READ),
      true,
      true),
  GLOBAL_EDITOR(
      List.of("monteis-client:experiment:write:all"),
      List.of(Grant.EXPERIMENT_WRITE_ALL),
      false,
      false),
  MONTEIS_ADMIN(
      List.of("monteis-client:admin", "monteis-client:documents:read"),
      List.of(Grant.ADMIN, Grant.DOCUMENTS_READ),
      false,
      false);

  public static final UUID ASSIGNED_EXPERIMENT =
      UUID.fromString("00000000-0000-7000-8000-000000000301");
  public static final UUID OTHER_EXPERIMENT =
      UUID.fromString("00000000-0000-7000-8000-000000000303");
  public static final UUID UNASSIGNED_EXPERIMENT =
      UUID.fromString("00000000-0000-7000-8000-000000000302");

  private final List<String> roles;
  private final List<Grant> authorities;
  private final boolean carriesReadIds;
  private final boolean carriesWriteIds;

  PrivilegeLevel(
      List<String> roles,
      List<Grant> authorities,
      boolean carriesReadIds,
      boolean carriesWriteIds) {
    this.roles = roles;
    this.authorities = authorities;
    this.carriesReadIds = carriesReadIds;
    this.carriesWriteIds = carriesWriteIds;
  }

  /** The {@code monteis_access.roles} a token of this level carries. */
  public List<String> roles() {
    return roles;
  }

  public List<GrantedAuthority> grantedAuthorities() {
    return List.copyOf(authorities);
  }

  public List<UUID> readExperimentIds() {
    return carriesReadIds ? List.of(ASSIGNED_EXPERIMENT, OTHER_EXPERIMENT) : List.of();
  }

  public List<UUID> writeExperimentIds() {
    return carriesWriteIds ? List.of(ASSIGNED_EXPERIMENT) : List.of();
  }

  /** The authentication {@code MonteisJwtAuthenticationConverter} produces for this level. */
  public MonteisAuthenticationToken authentication() {
    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.randomUUID(), name().toLowerCase(), readExperimentIds(), writeExperimentIds());
    return new MonteisAuthenticationToken(null, principal, grantedAuthorities());
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
