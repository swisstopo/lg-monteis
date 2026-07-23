package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.WRITE_AUTHORITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verifies the full JWT-to-{@code Authentication} mapping in {@link MonteisJwtAuthenticationConverter}. */
class MonteisJwtAuthenticationConverterTest {

  private final MonteisJwtAuthenticationConverter converter =
      new MonteisJwtAuthenticationConverter();

  @Test
  void should_mark_the_resulting_token_as_authenticated() {
    // given
    Jwt jwt = givenJwt(UUID.randomUUID(), "alice", List.of("user"), List.of(1L));

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
    // given: a dedicated token type, not e.g. UsernamePasswordAuthenticationToken, since this app
    // authenticates OAuth2 bearer JWTs, not a username/password exchange
    Jwt jwt = givenJwt(UUID.randomUUID(), "alice", List.of("user"), List.of(1L));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertInstanceOf(MonteisAuthenticationToken.class, authentication);
    assertEquals(jwt, authentication.getCredentials());
  }

  @Test
  void should_populate_own_scope_principal_from_user_role() {
    // given
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "alice", List.of("user"), List.of(1L, 2L));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(subject, "alice", List.of(1L, 2L)), authentication.getPrincipal());
    assertEquals(Set.of(new SimpleGrantedAuthority(READ_AUTHORITY)), authoritiesOf(authentication));
  }

  @Test
  void should_populate_read_all_principal_from_admin_role() {
    // given
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "bob", List.of("admin"), List.of(5L));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "bob", List.of(5L)), authentication.getPrincipal());
    assertEquals(
        Set.of(
            new SimpleGrantedAuthority(WRITE_AUTHORITY),
            new SimpleGrantedAuthority(READ_ALL_AUTHORITY)),
        authoritiesOf(authentication));
  }

  @Test
  void should_deny_experiment_ids_when_realm_access_claim_missing() {
    // given: experiment_ids present despite the missing role claim (and so no read authority at
    // all), to prove it's still forced empty rather than leaking through
    UUID subject = UUID.randomUUID();
    Jwt jwt = givenJwt(subject, "carol", null, List.of(1L, 2L));

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "carol", List.of()), authentication.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(authentication));
  }

  @Test
  void should_deny_experiment_ids_when_roles_are_not_all_strings() {
    // given
    UUID subject = UUID.randomUUID();
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", "dave");
    claims.put("realm_access", Map.of("roles", List.of("admin", 123)));
    Jwt jwt = givenJwtWithClaims(subject, claims);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(new MonteisPrincipal(subject, "dave", List.of()), authentication.getPrincipal());
    assertEquals(Set.of(), authoritiesOf(authentication));
  }

  @Test
  void should_filter_out_non_numeric_experiment_ids() {
    // given
    UUID subject = UUID.randomUUID();
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", "erin");
    claims.put("realm_access", Map.of("roles", List.of("user")));
    claims.put("experiment_ids", List.of(1L, "not-a-number", 2L));
    Jwt jwt = givenJwtWithClaims(subject, claims);

    // when
    AbstractAuthenticationToken authentication = converter.convert(jwt);

    // then
    assertEquals(
        new MonteisPrincipal(subject, "erin", List.of(1L, 2L)), authentication.getPrincipal());
  }

  private static Set<GrantedAuthority> authoritiesOf(AbstractAuthenticationToken authentication) {
    return new HashSet<>(authentication.getAuthorities());
  }

  private static Jwt givenJwt(
      UUID subject, String username, List<String> roles, List<Long> experimentIds) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", username);
    if (roles != null) {
      claims.put("realm_access", Map.of("roles", roles));
    }
    if (experimentIds != null) {
      claims.put("experiment_ids", experimentIds);
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
