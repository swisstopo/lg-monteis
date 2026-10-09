package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * The HTTP request rules (BR4.8), evaluated in order, first match wins. Reads and experiment
 * updates are only authenticated here, because row-level security filters the rows and decides
 * which experiments the caller may update (hybrid filter-chain-plus-RLS model, ADR-003).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**", "/actuator", "/swagger-ui/**", "/v3/api-docs/**"
  };
  static final String EXPERIMENT_PATH = "/api/experiments/{id}";

  // every method that changes state; all of them need write-all unless an earlier rule matched
  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final RequestMatcher WRITE_REQUESTS =
      request -> WRITE_METHODS.contains(request.getMethod());

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
                    // the experiments_update RLS policy decides which experiments may be written
                    .requestMatchers(HttpMethod.PUT, EXPERIMENT_PATH)
                    .authenticated()
                    .requestMatchers(WRITE_REQUESTS)
                    .hasAuthority(Permissions.WRITE_ALL)
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(new MonteisAccessDeniedHandler(objectMapper)))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return new MonteisJwtAuthenticationConverter();
  }
}
