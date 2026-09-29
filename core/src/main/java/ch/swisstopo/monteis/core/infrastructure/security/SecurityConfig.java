package ch.swisstopo.monteis.core.infrastructure.security;

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
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP request rules (BR4.8), evaluated in order, first match wins. Every non-public decision
 * asks {@link AccessPolicy}; reads are only authenticated here, because row-level security filters
 * the rows (hybrid filter-chain-plus-RLS model, ADR-003).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**", "/actuator", "/swagger-ui/**", "/v3/api-docs/**"
  };
  static final String EXPERIMENTS_PATH = "/api/experiments";
  static final String SENSORS_PATHS = "/api/sensors/**";

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
      ObjectMapper objectMapper) {
    http.authorizeHttpRequests(
            request ->
                request
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    // Per-experiment edit check; the experiments_update RLS policy enforces the
                    // same rule in the database. Must precede the admin-only write rules below.
                    .requestMatchers(HttpMethod.PUT, ExperimentWriteAuthorizationManager.PATH)
                    .access(new ExperimentWriteAuthorizationManager())
                    .requestMatchers(HttpMethod.POST, EXPERIMENTS_PATH)
                    .access(allowIf(Capabilities::canCreateExperiment))
                    // the whole sensor catalogue, reads included, is admin-only (FR2.6)
                    .requestMatchers(SENSORS_PATHS)
                    .access(allowIf(Capabilities::canManageSensors))
                    .requestMatchers(HttpMethod.POST)
                    .access(allowIf(Capabilities::isAdmin))
                    .requestMatchers(HttpMethod.PUT)
                    .access(allowIf(Capabilities::isAdmin))
                    .requestMatchers(HttpMethod.PATCH)
                    .access(allowIf(Capabilities::isAdmin))
                    .requestMatchers(HttpMethod.DELETE)
                    .access(allowIf(Capabilities::isAdmin))
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(new MonteisAccessDeniedHandler(objectMapper)))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .accessDeniedHandler(new MonteisAccessDeniedHandler(objectMapper)));

    return http.build();
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return new MonteisJwtAuthenticationConverter();
  }

  private static AuthorizationManager<RequestAuthorizationContext> allowIf(
      Predicate<Capabilities> capability) {
    return (authentication, context) ->
        new AuthorizationDecision(
            capability.test(AccessPolicy.capabilitiesOf(authentication.get())));
  }
}
