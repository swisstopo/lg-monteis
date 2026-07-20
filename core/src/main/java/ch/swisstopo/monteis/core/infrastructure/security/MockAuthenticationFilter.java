package ch.swisstopo.monteis.core.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * POC-only stand-in for real authentication: unconditionally attaches a statically mocked JWT
 * principal to every request, no {@code Authorization} header or parsing involved at all. Edit
 * the two constants below and restart the app to test a different role/experiment-membership
 * scenario. {@link SecurityContextFilter} reads the resulting principal exactly as it would a
 * real one — nothing downstream of {@code SecurityContextHolder} knows or cares that this is a
 * mock.
 *
 * <p>Once Keycloak is wired up for real authentication, delete this filter and its registration
 * in {@code SecurityConfig}, and add a real {@code JwtDecoder} bean + {@code
 * .oauth2ResourceServer(oauth2 -> oauth2.jwt(...))} instead.
 */
public class MockAuthenticationFilter extends OncePerRequestFilter {

  // Edit these two lines by hand to change the mocked identity, then restart the app.
  private static final List<String> MOCK_ROLES = List.of(AccessLevel.ADMIN.toString());
  private static final List<Long> MOCK_EXPERIMENT_IDS = List.of(1L, 2L, 3L);

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    Instant now = Instant.now();
    Jwt mockJwt =
        Jwt.withTokenValue("mock")
            .headers(headers -> headers.put("alg", "none"))
            .claim("realm_access", Map.of("roles", MOCK_ROLES))
            .claim("experiment_ids", MOCK_EXPERIMENT_IDS)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();

    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(mockJwt));

    filterChain.doFilter(request, response);
  }
}
