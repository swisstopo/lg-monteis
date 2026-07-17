package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class MockAuthenticationFilterTest {

  private final MockAuthenticationFilter filter = new MockAuthenticationFilter();

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_attach_a_jwt_principal_to_the_security_context_holder() throws Exception {
    // given
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(authentication);
    assertInstanceOf(Jwt.class, authentication.getPrincipal());
  }

  @Test
  void should_use_the_configured_mock_roles_and_experiment_ids_as_claims() throws Exception {
    // given
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // when
    filter.doFilterInternal(request, response, chain);

    // then: mirrors MockAuthenticationFilter's current MOCK_ROLES/MOCK_EXPERIMENT_IDS constants —
    // update both together if those change.
    Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    assertNotNull(jwt);
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    assertNotNull(realmAccess);
    assertEquals(List.of(AccessLevel.ADMIN.toString()), realmAccess.get("roles"));
    assertEquals(List.of(1L, 2L, 3L), jwt.getClaim("experiment_ids"));
  }

  @Test
  void should_continue_the_filter_chain() throws Exception {
    // given
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // when
    filter.doFilterInternal(request, response, chain);

    // then
    then(chain).should().doFilter(request, response);
  }
}
