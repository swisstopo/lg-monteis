package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class MonteisAuthenticationTokenTest {

  private static final GrantedAuthority READ = new SimpleGrantedAuthority("experiment:read");
  private static final GrantedAuthority WRITE_ALL =
      new SimpleGrantedAuthority("experiment:write:all");

  private static final UUID EXPERIMENT_1 = UUID.randomUUID();
  private static final UUID EXPERIMENT_2 = UUID.randomUUID();

  @Test
  void should_carry_the_jwt_as_credentials_and_the_principal() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");

    // when
    var token = new MonteisAuthenticationToken(jwt, principal, List.of(READ), Set.of(), Set.of());

    // then
    assertSame(jwt, token.getCredentials());
    assertSame(principal, token.getPrincipal());
    assertEquals("alice", token.getName());
    assertTrue(token.isAuthenticated());
  }

  @Test
  void should_be_equal_when_jwt_principal_and_authorities_match() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);

    // when
    var first = new MonteisAuthenticationToken(jwt, principal, authorities, Set.of(), Set.of());
    var second = new MonteisAuthenticationToken(jwt, principal, authorities, Set.of(), Set.of());

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
            jwt, new MonteisPrincipal(UUID.randomUUID(), "alice"), authorities, Set.of(), Set.of());
    var second =
        new MonteisAuthenticationToken(
            jwt, new MonteisPrincipal(UUID.randomUUID(), "bob"), authorities, Set.of(), Set.of());

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
        new MonteisAuthenticationToken(jwt, principal, authorities, Set.of(EXPERIMENT_1), Set.of());
    var second =
        new MonteisAuthenticationToken(jwt, principal, authorities, Set.of(EXPERIMENT_2), Set.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_jwt_differs() {
    // given
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var authorities = List.of(READ);
    var first =
        new MonteisAuthenticationToken(givenJwt(), principal, authorities, Set.of(), Set.of());
    var second =
        new MonteisAuthenticationToken(givenJwt(), principal, authorities, Set.of(), Set.of());

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_authorities_differ() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice");
    var first = new MonteisAuthenticationToken(jwt, principal, List.of(READ), Set.of(), Set.of());
    var second =
        new MonteisAuthenticationToken(jwt, principal, List.of(WRITE_ALL), Set.of(), Set.of());

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
        new MonteisAuthenticationToken(jwt, principal, authorities, Set.of(), Set.of());
    var otherToken = UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);

    // then
    assertNotEquals(monteisToken, otherToken);
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
