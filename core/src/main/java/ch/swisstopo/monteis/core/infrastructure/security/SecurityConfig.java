package ch.swisstopo.monteis.core.infrastructure.security;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP request rules (BR4.8), evaluated in order, first match wins. Every non-public decision
 * asks {@link Capabilities}; reads are only authenticated here, because row-level security filters
 * the rows (hybrid filter-chain-plus-RLS model, ADR-003).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**", "/actuator", "/swagger-ui/**", "/v3/api-docs/**"
  };
  static final String SENSORS_PATHS = ApiPaths.SENSORS + "/**";
  static final String EXPERIMENT_OWNER_CANDIDATES_PATH = "/api/experiments/{id}/owner-candidates";
  static final String EXPERIMENT_OWNERS_PATH = "/api/experiments/{id}/owners";

  // every method that changes state; all of them are admin-only unless an earlier rule matched
  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final RequestMatcher WRITE_REQUESTS =
      request -> WRITE_METHODS.contains(request.getMethod());

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
      ObjectMapper objectMapper) {
    MonteisAccessDeniedHandler accessDeniedHandler = new MonteisAccessDeniedHandler(objectMapper);
    ExperimentWriteAuthorizationManager experimentWrite = new ExperimentWriteAuthorizationManager();
    http.authorizeHttpRequests(
            request ->
                request
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    // first match wins, the per-experiment writes have to come before the
                    // admin-only write rule below
                    .requestMatchers(EXPERIMENT_OWNER_CANDIDATES_PATH, EXPERIMENT_OWNERS_PATH)
                    .access(allowIf(Capabilities::canManageExperimentOwners))
                    .requestMatchers(HttpMethod.PUT, ApiPaths.EXPERIMENT)
                    .access(experimentWrite)
                    .requestMatchers(HttpMethod.POST, ApiPaths.EXPERIMENT_DOCUMENTS)
                    .access(experimentWrite)
                    .requestMatchers(HttpMethod.POST, ApiPaths.EXPERIMENTS)
                    .access(allowIf(Capabilities::canCreateExperiment))
                    // sensor reads need no rule of their own: RLS filters the rows by experiment
                    .requestMatchers(HttpMethod.GET, SENSORS_PATHS)
                    .authenticated()
                    .requestMatchers(SENSORS_PATHS)
                    .access(allowIf(Capabilities::canManageSensors))
                    .requestMatchers(WRITE_REQUESTS)
                    .access(allowIf(Capabilities::isAdmin))
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(accessDeniedHandler))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .accessDeniedHandler(accessDeniedHandler));

    return http.build();
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return new MonteisJwtAuthenticationConverter();
  }

  private static AuthorizationManager<RequestAuthorizationContext> allowIf(
      Predicate<Capabilities> capability) {
    return (authentication, context) ->
        new AuthorizationDecision(capability.test(Capabilities.of(authentication.get())));
  }
}
