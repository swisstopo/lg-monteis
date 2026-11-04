package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class MonteisAuthenticationTokenTest {

  private static final GrantedAuthority READ =
      new SimpleGrantedAuthority(Permissions.EXPERIMENT_READ);
  private static final GrantedAuthority WRITE =
      new SimpleGrantedAuthority(Permissions.EXPERIMENT_WRITE);
  private static final GrantedAuthority WRITE_ALL =
      new SimpleGrantedAuthority(Permissions.WRITE_ALL);

  private static final UUID EXPERIMENT_1 = UUID.randomUUID();
  private static final UUID EXPERIMENT_2 = UUID.randomUUID();

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_derive_the_permissions_of_each_privilege_level(PrivilegeLevel level) {
    // when
    MonteisAuthenticationToken token = level.authentication();

    // then
    assertEquals(level == PrivilegeLevel.MONTEIS_ADMIN, token.canWriteAll());
    assertEquals(level.readableExperimentIds(), token.readableExperimentIds());
    assertEquals(Set.copyOf(level.writeExperimentIds()), token.writableExperimentIds());
  }

  @Test
  void should_scope_reads_and_writes_to_the_assigned_experiments() {
    // when
    var token =
        givenToken(
            List.of(READ, WRITE), List.of(EXPERIMENT_1, EXPERIMENT_2), List.of(EXPERIMENT_1));

    // then
    assertFalse(token.canWriteAll());
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readableExperimentIds());
    assertEquals(Set.of(EXPERIMENT_1), token.writableExperimentIds());
  }

  @Test
  void should_let_the_caller_read_the_experiments_it_may_write() {
    // when
    var token = givenToken(List.of(READ, WRITE), List.of(EXPERIMENT_2), List.of(EXPERIMENT_1));

    // then
    assertEquals(Set.of(EXPERIMENT_1, EXPERIMENT_2), token.readableExperimentIds());
    assertEquals(Set.of(EXPERIMENT_1), token.writableExperimentIds());
  }

  @Test
  void should_ignore_assigned_ids_without_the_matching_permission() {
    // when
    var withoutWrite = givenToken(List.of(READ), List.of(EXPERIMENT_1), List.of(EXPERIMENT_1));
    var withoutAny = givenToken(List.of(), List.of(EXPERIMENT_1), List.of(EXPERIMENT_1));

    // then
    assertEquals(Set.of(), withoutWrite.writableExperimentIds());
    assertEquals(Set.of(), withoutAny.readableExperimentIds());
    assertEquals(Set.of(), withoutAny.writableExperimentIds());
  }

  @Test
  void should_leave_the_id_sets_empty_when_write_all_applies() {
    // when
    var token =
        givenToken(List.of(WRITE_ALL, READ, WRITE), List.of(EXPERIMENT_1), List.of(EXPERIMENT_1));

    // then
    assertTrue(token.canWriteAll());
    assertEquals(Set.of(), token.readableExperimentIds());
    assertEquals(Set.of(), token.writableExperimentIds());
  }

  @Test
  void should_hand_out_the_same_id_sets_on_every_call() {
    // given
    var token =
        givenToken(
            List.of(READ, WRITE), List.of(EXPERIMENT_1, EXPERIMENT_2), List.of(EXPERIMENT_1));

    // when / then
    assertSame(token.readableExperimentIds(), token.readableExperimentIds());
    assertSame(token.writableExperimentIds(), token.writableExperimentIds());
  }

  @Test
  void should_ignore_unknown_authorities() {
    // when
    var token =
        givenToken(
            List.of(new SimpleGrantedAuthority("api:admin")), List.of(EXPERIMENT_1), List.of());

    // then
    assertFalse(token.canWriteAll());
    assertEquals(Set.of(), token.readableExperimentIds());
  }

  @Test
  void should_be_equal_when_jwt_principal_and_authorities_match() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);

    // when
    var first = new MonteisAuthenticationToken(jwt, principal, authorities, List.of(), List.of());
    var second = new MonteisAuthenticationToken(jwt, principal, authorities, List.of(), List.of());

    // then
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void should_not_be_equal_when_principal_differs() {
    // given
    Jwt jwt = givenJwt();
    var authorities = List.of(READ);
    var first =
        new MonteisAuthenticationToken(
            jwt,
            new MonteisPrincipal(UUID.randomUUID(), "alice"),
            authorities,
            List.of(),
            List.of());
    var second =
        new MonteisAuthenticationToken(
            jwt, new MonteisPrincipal(UUID.randomUUID(), "bob"), authorities, List.of(), List.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_the_experiment_ids_differ() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);
    var first =
        new MonteisAuthenticationToken(
            jwt, principal, authorities, List.of(EXPERIMENT_1), List.of());
    var second =
        new MonteisAuthenticationToken(
            jwt, principal, authorities, List.of(EXPERIMENT_2), List.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_jwt_differs() {
    // given
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);
    var first =
        new MonteisAuthenticationToken(givenJwt(), principal, authorities, List.of(), List.of());
    var second =
        new MonteisAuthenticationToken(givenJwt(), principal, authorities, List.of(), List.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_authorities_differ() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var first = new MonteisAuthenticationToken(jwt, principal, List.of(READ), List.of(), List.of());
    var second =
        new MonteisAuthenticationToken(jwt, principal, List.of(WRITE_ALL), List.of(), List.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_to_a_different_authentication_type_with_the_same_fields() {
    // given: without our override, AbstractAuthenticationToken#equals would treat this as equal
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);
    var monteisToken =
        new MonteisAuthenticationToken(jwt, principal, authorities, List.of(), List.of());
    var otherToken = UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);

    // then
    assertNotEquals(monteisToken, otherToken);
  }

  private static MonteisAuthenticationToken givenToken(
      List<GrantedAuthority> authorities,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds) {
    return new MonteisAuthenticationToken(
        null,
        new MonteisPrincipal(UUID.randomUUID(), "alice"),
        authorities,
        readExperimentIds,
        writeExperimentIds);
  }

  private static Jwt givenJwt() {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test")
        .header("alg", "none")
        .subject(UUID.randomUUID().toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build();
  }
}
