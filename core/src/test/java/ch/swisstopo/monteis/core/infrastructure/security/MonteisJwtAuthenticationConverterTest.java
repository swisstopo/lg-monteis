package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verifies the full JWT-to-{@code Authentication} mapping in {@link MonteisJwtAuthenticationConverter}. */
class MonteisJwtAuthenticationConverterTest {

  private static final UUID EXPERIMENT_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID EXPERIMENT_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID EXPERIMENT_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

  private static final String READ = "monteis-client:experiment:read";
  private static final String WRITE = "monteis-client:experiment:write";
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
    var token = (MonteisAuthenticationToken) converter.convert(level.jwt("token"));

    // then
    assertEquals(
        level.grantedAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet()),
        authoritiesOf(token));
    assertEquals(level.readableExperimentIds(), token.readExperimentIds());
    assertEquals(Set.copyOf(level.writeExperimentIds()), token.writeExperimentIds());
  }

  @Test
  void should_scope_an_experiment_user_to_the_experiments_it_may_read() {
    // given
    UUID subject = UUID.randomUUID();
    Jwt jwt =
        givenJwt(subject, "bob", List.of(READ), List.of(EXPERIMENT_1, EXPERIMENT_2), List.of());

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "bob"), token.getPrincipal());
    assertEquals(Set.of("experiment:read"), authoritiesOf(token));
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
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
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "alice"), token.getPrincipal());
    assertEquals(Set.of("experiment:read", "experiment:write"), authoritiesOf(token));
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readExperimentIds());
    assertEquals(Set.of(EXPERIMENT_1), token.writeExperimentIds());
  }

  @Test
  void should_map_admin_to_read_all_and_write_all_and_keep_no_experiment_ids() {
    // given: the :all authorities cover every experiment, so the id claims are irrelevant
    UUID subject = UUID.randomUUID();
    Jwt jwt =
        givenJwt(subject, "root", List.of(ADMIN), List.of(EXPERIMENT_5), List.of(EXPERIMENT_5));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "root"), token.getPrincipal());
    assertEquals(Set.of("experiment:read:all", "experiment:write:all"), authoritiesOf(token));
    assertEquals(Set.of(), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
  }

  @Test
  void should_not_narrow_an_admin_who_is_also_a_pi_to_its_experiments() {
    // given
    Jwt jwt =
        givenJwt(
            UUID.randomUUID(),
            "root",
            List.of(ADMIN, READ, WRITE),
            List.of(EXPERIMENT_1),
            List.of(EXPERIMENT_2));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(
        Set.of(
            "experiment:read", "experiment:write", "experiment:read:all", "experiment:write:all"),
        authoritiesOf(token));
    assertEquals(Set.of(), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
  }

  @Test
  void should_ignore_unknown_and_removed_roles() {
    // given
    Jwt jwt =
        givenJwt(
            UUID.randomUUID(),
            "legacy",
            List.of(
                "monteis-client:experiment:write:all",
                "monteis-client:documents:read",
                "monteis-client:read-all",
                "monteis-client:write"),
            List.of(EXPERIMENT_1),
            List.of(EXPERIMENT_1));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(Set.of(), authoritiesOf(token));
    assertEquals(Set.of(), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
  }

  @Test
  void should_imply_read_for_the_write_role() {
    // given
    Jwt jwt = givenJwt(UUID.randomUUID(), "eve", List.of(WRITE), List.of(), List.of(EXPERIMENT_1));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(Set.of("experiment:read", "experiment:write"), authoritiesOf(token));
    assertEquals(Set.of(EXPERIMENT_1), token.readExperimentIds());
    assertEquals(Set.of(EXPERIMENT_1), token.writeExperimentIds());
  }

  @Test
  void should_drop_write_ids_without_the_write_role() {
    // given
    Jwt jwt =
        givenJwt(
            UUID.randomUUID(), "bob", List.of(READ), List.of(EXPERIMENT_1), List.of(EXPERIMENT_2));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(Set.of(EXPERIMENT_1), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
  }

  @Test
  void should_let_the_caller_read_the_experiments_it_may_write() {
    // given
    Jwt jwt =
        givenJwt(
            UUID.randomUUID(),
            "mallory",
            List.of(READ, WRITE),
            List.of(EXPERIMENT_1),
            List.of(EXPERIMENT_1, EXPERIMENT_2));

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readExperimentIds());
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.writeExperimentIds());
  }

  @Test
  void should_deny_experiment_ids_when_monteis_access_claim_missing() {
    // given: ids present despite the missing role claim, to prove they are still forced empty
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "carol", null, List.of(EXPERIMENT_1, EXPERIMENT_2), null);

    // when
    var token = (MonteisAuthenticationToken) converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "carol"), token.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(token));
    assertEquals(Set.of(), token.readExperimentIds());
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
    assertEquals(new MonteisPrincipal(subject, "dave"), authentication.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(authentication));
  }

  @Test
  void should_deny_everything_when_roles_is_not_a_list() {
    // given
    Jwt jwt =
        givenJwtWithClaims(UUID.randomUUID(), Map.of("monteis_access", Map.of("roles", ADMIN)));

    // then
    assertEquals(Set.of(), authoritiesOf(converter.convert(jwt)));
  }

  @Test
  void should_deny_everything_when_the_monteis_access_claim_is_not_an_object() {
    // given
    Jwt jwt = givenJwtWithClaims(UUID.randomUUID(), Map.of("monteis_access", "admin"));

    // then
    assertEquals(Set.of(), authoritiesOf(converter.convert(jwt)));
  }

  @Test
  void should_drop_non_uuid_null_and_duplicate_experiment_ids_one_by_one() {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put("monteis_access", Map.of("roles", List.of(READ)));
    claims.put(
        "read_experiment_ids",
        Arrays.asList(
            EXPERIMENT_1.toString(),
            "not-a-uuid",
            42,
            null,
            EXPERIMENT_2.toString(),
            EXPERIMENT_1.toString()));

    // when
    var token =
        (MonteisAuthenticationToken)
            converter.convert(givenJwtWithClaims(UUID.randomUUID(), claims));

    // then
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readExperimentIds());
  }

  @Test
  void should_yield_no_experiment_ids_when_the_id_claims_are_not_lists() {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put("monteis_access", Map.of("roles", List.of(READ, WRITE)));
    claims.put("read_experiment_ids", EXPERIMENT_1.toString());
    claims.put("write_experiment_ids", EXPERIMENT_2.toString());

    // when
    var token =
        (MonteisAuthenticationToken)
            converter.convert(givenJwtWithClaims(UUID.randomUUID(), claims));

    // then
    assertEquals(Set.of(), token.readExperimentIds());
    assertEquals(Set.of(), token.writeExperimentIds());
  }

  @Test
  void should_ignore_the_removed_experiment_ids_claim() {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put("monteis_access", Map.of("roles", List.of(READ)));
    claims.put("experiment_ids", List.of(EXPERIMENT_1.toString()));

    // when
    var token =
        (MonteisAuthenticationToken)
            converter.convert(givenJwtWithClaims(UUID.randomUUID(), claims));

    // then
    assertEquals(Set.of(), token.readExperimentIds());
  }

  @Test
  void should_reject_a_subject_that_is_not_a_uuid() {
    // given
    Jwt jwt =
        Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject("not-a-uuid")
            .claim("preferred_username", "eve")
            .build();

    // when / then
    assertThrows(IllegalArgumentException.class, () -> converter.convert(jwt));
  }

  private static Set<String> authoritiesOf(AbstractAuthenticationToken authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
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
