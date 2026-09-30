package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verifies the full JWT-to-{@code Authentication} mapping in {@link MonteisJwtAuthenticationConverter}. */
class MonteisJwtAuthenticationConverterTest {

  private static final UUID EXPERIMENT_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID EXPERIMENT_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID EXPERIMENT_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

  private static final String READ = "monteis-client:experiment:read";
  private static final String WRITE = "monteis-client:experiment:write";
  private static final String WRITE_ALL = "monteis-client:experiment:write:all";
  private static final String ADMIN = "monteis-client:admin";

  private final MonteisJwtAuthenticationConverter converter =
      new MonteisJwtAuthenticationConverter();

  @Test
  void should_mark_the_resulting_token_as_authenticated() {
    // given
    Jwt jwt = givenJwt(UUID.randomUUID(), "alice", List.of(READ), List.of(EXPERIMENT_1), null);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertTrue(
        authentication.isAuthenticated(),
        "a valid JWT must produce an authenticated token, or anyRequest().authenticated() rejects"
            + " it regardless of a correctly-decoded JWT and correct authorities");
  }

  @Test
  void should_return_a_monteis_authentication_token_carrying_the_source_jwt_as_credentials() {
    // given
    Jwt jwt = givenJwt(UUID.randomUUID(), "alice", List.of(READ), List.of(EXPERIMENT_1), null);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertInstanceOf(MonteisAuthenticationToken.class, authentication);
    assertEquals(jwt, authentication.getCredentials());
  }

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_convert_a_token_of_each_privilege_level(PrivilegeLevel level) {
    // when
    AbstractAuthenticationToken authentication = converter.convert(level.jwt("token"));
    MonteisPrincipal principal = (MonteisPrincipal) authentication.getPrincipal();

    // then
    assertEquals(Set.copyOf(level.grantedAuthorities()), authoritiesOf(authentication));
    assertEquals(level.readExperimentIds(), principal.readExperimentIds());
    assertEquals(level.writeExperimentIds(), principal.writeExperimentIds());
  }

  @Test
  void should_populate_a_scoped_principal_from_read_and_write_roles() {
    // given
    UUID subject = UUID.randomUUID();
    Jwt jwt =
        givenJwt(
            subject,
            "alice",
            List.of(READ, WRITE),
            List.of(EXPERIMENT_1, EXPERIMENT_2),
            List.of(EXPERIMENT_1));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(
            subject, "alice", List.of(EXPERIMENT_1, EXPERIMENT_2), List.of(EXPERIMENT_1)),
        authentication.getPrincipal());
    assertEquals(
        Set.of(Grant.EXPERIMENT_READ, Grant.EXPERIMENT_WRITE), authoritiesOf(authentication));
  }

  @Test
  void should_accept_write_all_alone_and_keep_no_experiment_ids() {
    // given: write:all implies all-experiment read, so its id claims are irrelevant
    UUID subject = UUID.randomUUID();
    Jwt jwt =
        givenJwt(
            subject, "editor", List.of(WRITE_ALL), List.of(EXPERIMENT_5), List.of(EXPERIMENT_5));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(subject, "editor", List.of(), List.of()),
        authentication.getPrincipal());
    assertEquals(Set.of(Grant.EXPERIMENT_WRITE_ALL), authoritiesOf(authentication));
  }

  @Test
  void should_reject_write_role_without_read_role() {
    Jwt jwt = givenJwt(UUID.randomUUID(), "eve", List.of(WRITE), List.of(), List.of());

    assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
  }

  @Test
  void should_drop_write_ids_without_the_write_role() {
    // given
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "bob", List.of(READ), List.of(EXPERIMENT_1), List.of(EXPERIMENT_1));

    // when
    MonteisPrincipal principal = (MonteisPrincipal) converter.convert(jwt).getPrincipal();

    // then
    assertEquals(List.of(EXPERIMENT_1), principal.readExperimentIds());
    assertEquals(List.of(), principal.writeExperimentIds());
  }

  @Test
  void should_drop_write_ids_that_are_not_also_read_ids() {
    // given: a tampered or misconfigured token
    Jwt jwt =
        givenJwt(
            UUID.randomUUID(),
            "mallory",
            List.of(READ, WRITE),
            List.of(EXPERIMENT_1),
            List.of(EXPERIMENT_1, EXPERIMENT_2));

    // when
    MonteisPrincipal principal = (MonteisPrincipal) converter.convert(jwt).getPrincipal();

    // then
    assertEquals(List.of(EXPERIMENT_1), principal.writeExperimentIds());
  }

  @Test
  void should_deny_experiment_ids_when_monteis_access_claim_missing() {
    // given: ids present despite the missing role claim, to prove they are still forced empty
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "carol", null, List.of(EXPERIMENT_1, EXPERIMENT_2), null);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(subject, "carol", List.of(), List.of()),
        authentication.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(authentication));
  }

  @Test
  void should_deny_everything_when_roles_are_not_all_strings() {
    // given
    UUID subject = UUID.randomUUID();
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", "dave");
    claims.put("monteis_access", Map.of("roles", List.of(ADMIN, 123)));
    claims.put("read_experiment_ids", List.of(EXPERIMENT_1.toString()));
    Jwt jwt = givenJwtWithClaims(subject, claims);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(subject, "dave", List.of(), List.of()), authentication.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(authentication));
  }

  @Test
  void should_ignore_legacy_roles_and_the_legacy_experiment_ids_claim() {
    // given
    UUID subject = UUID.randomUUID();
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", "legacy");
    claims.put(
        "monteis_access",
        Map.of("roles", List.of("monteis-client:read-all", "monteis-client:write")));
    claims.put("experiment_ids", List.of(EXPERIMENT_1.toString()));
    Jwt jwt = givenJwtWithClaims(subject, claims);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(Set.of(), authoritiesOf(authentication));
    assertEquals(
        new MonteisPrincipal(subject, "legacy", List.of(), List.of()),
        authentication.getPrincipal());
  }

  @Test
  void should_grant_admin_without_needing_experiment_ids() {
    Jwt jwt = givenJwt(UUID.randomUUID(), "root", List.of(ADMIN), null, null);

    assertEquals(Set.of(Grant.ADMIN), authoritiesOf(converter.convert(jwt)));
  }

  private static Set<GrantedAuthority> authoritiesOf(AbstractAuthenticationToken authentication) {
    return new HashSet<>(authentication.getAuthorities());
  }

  private static Jwt givenJwt(
      UUID subject,
      String username,
      List<String> roles,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", username);
    if (roles != null) {
      claims.put("monteis_access", Map.of("roles", roles));
    }
    if (readExperimentIds != null) {
      claims.put("read_experiment_ids", readExperimentIds.stream().map(UUID::toString).toList());
    }
    if (writeExperimentIds != null) {
      claims.put("write_experiment_ids", writeExperimentIds.stream().map(UUID::toString).toList());
    }
    return givenJwtWithClaims(subject, claims);
  }

  private static Jwt givenJwtWithClaims(UUID subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test")
        .headers(headers -> headers.put("alg", "none"))
        .claims(c -> c.putAll(claims))
        .subject(subject.toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build();
  }
}
