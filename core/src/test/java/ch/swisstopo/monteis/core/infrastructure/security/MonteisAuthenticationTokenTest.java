package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class MonteisAuthenticationTokenTest {

  @Test
  void should_be_equal_when_jwt_principal_and_authorities_match() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice", List.of(1L));
    var authorities = List.of(new SimpleGrantedAuthority("api:read"));

    // when
    var first = new MonteisAuthenticationToken(jwt, principal, authorities);
    var second = new MonteisAuthenticationToken(jwt, principal, authorities);

    // then
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void should_not_be_equal_when_principal_differs() {
    // given
    Jwt jwt = givenJwt();
    var authorities = List.of(new SimpleGrantedAuthority("api:read"));
    var first =
        new MonteisAuthenticationToken(
            jwt, new MonteisPrincipal(UUID.randomUUID(), "alice", List.of(1L)), authorities);
    var second =
        new MonteisAuthenticationToken(
            jwt, new MonteisPrincipal(UUID.randomUUID(), "bob", List.of(1L)), authorities);

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_jwt_differs() {
    // given
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice", List.of(1L));
    var authorities = List.of(new SimpleGrantedAuthority("api:read"));
    var first = new MonteisAuthenticationToken(givenJwt(), principal, authorities);
    var second = new MonteisAuthenticationToken(givenJwt(), principal, authorities);

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_when_authorities_differ() {
    // given
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice", List.of(1L));
    var first =
        new MonteisAuthenticationToken(
            jwt, principal, List.of(new SimpleGrantedAuthority("api:read")));
    var second =
        new MonteisAuthenticationToken(
            jwt, principal, List.of(new SimpleGrantedAuthority("api:read-all")));

    // then
    assertNotEquals(first, second);
  }

  @Test
  void should_not_be_equal_to_a_different_authentication_type_with_the_same_fields() {
    // given: without our override, AbstractAuthenticationToken#equals would treat this as equal
    Jwt jwt = givenJwt();
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "alice", List.of(1L));
    var authorities = List.of(new SimpleGrantedAuthority("api:read"));
    var monteisToken = new MonteisAuthenticationToken(jwt, principal, authorities);
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
