package ch.swisstopo.monteis.core.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates {@link SecurityContext#CURRENT} for the duration of a request from the resolved JWT,
 * and nothing else reads the JWT anywhere in the application. Must be wired into the Spring
 * Security filter chain via {@code HttpSecurity.addFilterAfter(...)}, positioned after the OAuth2
 * resource-server filter so {@code SecurityContextHolder} already carries the authenticated
 * {@link Jwt} principal by the time this filter runs. Deliberately not a {@code @Component} to
 * avoid Spring Boot also auto-registering it as a generic servlet filter with the wrong ordering.
 */
public class SecurityContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    SecurityContext context = resolveSecurityContext();

    try {
      ScopedValue.where(SecurityContext.CURRENT, context)
          .call(
              () -> {
                filterChain.doFilter(request, response);
                return null;
              });
    } catch (IOException | ServletException e) {
      throw e;
    } catch (Exception e) {
      // filterChain.doFilter only ever declares IOException/ServletException; this branch only
      // exists because ScopedValue.Carrier#call widens to Exception, not because it's reachable.
      throw new IllegalStateException(e);
    }
  }

  private SecurityContext resolveSecurityContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return SecurityContext.DENY_ALL;
    }

    return new SecurityContext(extractAccessLevel(jwt), extractExperimentIds(jwt));
  }

  private AccessLevel extractAccessLevel(Jwt jwt) {
    List<String> roles = extractRoles(jwt);
    if (roles == null) {
      return AccessLevel.NONE;
    }
    if (roles.contains(AccessLevel.ADMIN.toString())) {
      return AccessLevel.ADMIN;
    }
    if (roles.contains(AccessLevel.USER.toString())) {
      return AccessLevel.USER;
    }
    return AccessLevel.NONE;
  }

  /** Returns null if {@code realm_access.roles} is missing or isn't a list of strings. */
  private static List<String> extractRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> rawRoles)) {
      return null;
    }
    List<String> roles = new ArrayList<>(rawRoles.size());
    for (Object role : rawRoles) {
      if (!(role instanceof String roleName)) {
        return null;
      }
      roles.add(roleName);
    }
    return roles;
  }

  private Set<Long> extractExperimentIds(Jwt jwt) {
    List<?> experimentIds = jwt.getClaim("experiment_ids");
    if (experimentIds == null) {
      return Set.of();
    }
    return experimentIds.stream()
        .filter(Number.class::isInstance)
        .map(id -> ((Number) id).longValue())
        .collect(Collectors.toUnmodifiableSet());
  }
}
