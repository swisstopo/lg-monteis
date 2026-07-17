package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Verifies {@link SecurityContext#CURRENT} is populated correctly from the JWT resolved in
 * {@link SecurityContextHolder} — the population only happens for the duration of the wrapped
 * {@code filterChain.doFilter(...)} call, so every assertion here captures {@link
 * SecurityContext#current()} from inside a mocked filter chain, not after {@code
 * doFilterInternal} returns.
 */
class SecurityContextFilterTest {

  private final SecurityContextFilter filter = new SecurityContextFilter();

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_populate_user_context_from_valid_jwt() throws Exception {
    // given
    givenAuthenticatedJwt(List.of(AccessLevel.USER.toString()), List.of(1L, 2L));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(new SecurityContext(AccessLevel.USER, Set.of(1L, 2L)), captured.get());
  }

  @Test
  void should_populate_admin_context_when_admin_role_present() throws Exception {
    // given
    givenAuthenticatedJwt(List.of(AccessLevel.ADMIN.toString()), List.of(5L));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(new SecurityContext(AccessLevel.ADMIN, Set.of(5L)), captured.get());
  }

  @Test
  void should_prioritize_admin_when_both_roles_present() throws Exception {
    // given
    givenAuthenticatedJwt(
        List.of(AccessLevel.USER.toString(), AccessLevel.ADMIN.toString()), List.of());
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(AccessLevel.ADMIN, captured.get().accessLevel());
  }

  @Test
  void should_deny_all_when_no_authentication_present() throws Exception {
    // given
    SecurityContextHolder.clearContext();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(SecurityContext.DENY_ALL, captured.get());
  }

  @Test
  void should_deny_all_when_principal_is_not_a_jwt() throws Exception {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("someone", "n/a"));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(SecurityContext.DENY_ALL, captured.get());
  }

  @Test
  void should_default_to_none_when_realm_access_claim_missing() throws Exception {
    // given: experiment_ids present despite the missing role claim, to prove NONE still forces
    // it empty rather than leaking through
    givenAuthenticatedJwt(null, List.of(1L, 2L));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(new SecurityContext(AccessLevel.NONE, Set.of()), captured.get());
  }

  @Test
  void should_default_to_none_when_roles_are_not_all_strings() throws Exception {
    // given
    givenAuthenticatedJwtWithClaims(
        Map.of("realm_access", Map.of("roles", List.of(AccessLevel.ADMIN.toString(), 123))));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(AccessLevel.NONE, captured.get().accessLevel());
  }

  @Test
  void should_default_experiment_ids_to_empty_when_claim_missing() throws Exception {
    // given
    givenAuthenticatedJwt(List.of(AccessLevel.USER.toString()), null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(Set.of(), captured.get().experimentIds());
  }

  @Test
  void should_filter_out_non_numeric_experiment_ids() throws Exception {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put("realm_access", Map.of("roles", List.of(AccessLevel.USER.toString())));
    claims.put("experiment_ids", List.of(1L, "not-a-number", 2L));
    givenAuthenticatedJwtWithClaims(claims);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    AtomicReference<SecurityContext> captured = capturingContextDuring(chain, request, response);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    assertEquals(Set.of(1L, 2L), captured.get().experimentIds());
  }

  @Test
  void should_propagate_io_exception_thrown_by_the_filter_chain() throws Exception {
    // given
    givenAuthenticatedJwt(List.of(AccessLevel.USER.toString()), List.of());
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    doThrow(new IOException("boom")).when(chain).doFilter(request, response);

    // when / then
    assertThrows(IOException.class, () -> filter.doFilterInternal(request, response, chain));
  }

  @Test
  void should_propagate_servlet_exception_thrown_by_the_filter_chain() throws Exception {
    // given
    givenAuthenticatedJwt(List.of(AccessLevel.USER.toString()), List.of());
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

    // when / then
    assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
  }

  // --- Helper Methods ---

  private AtomicReference<SecurityContext> capturingContextDuring(
      FilterChain chain, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    AtomicReference<SecurityContext> captured = new AtomicReference<>();
    doAnswer(
            invocation -> {
              captured.set(SecurityContext.current());
              return null;
            })
        .when(chain)
        .doFilter(request, response);
    return captured;
  }

  private void givenAuthenticatedJwt(List<String> roles, List<Long> experimentIds) {
    Map<String, Object> claims = new HashMap<>();
    if (roles != null) {
      claims.put("realm_access", Map.of("roles", roles));
    }
    if (experimentIds != null) {
      claims.put("experiment_ids", experimentIds);
    }
    givenAuthenticatedJwtWithClaims(claims);
  }

  private void givenAuthenticatedJwtWithClaims(Map<String, Object> claims) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("test")
            .headers(headers -> headers.put("alg", "none"))
            .claims(c -> c.putAll(claims))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
