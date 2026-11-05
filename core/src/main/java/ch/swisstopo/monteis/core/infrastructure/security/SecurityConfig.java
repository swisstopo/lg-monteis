package ch.swisstopo.monteis.core.infrastructure.security;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads are only authenticated here, because row-level security filters the rows (hybrid filter-chain-plus-RLS
 * model, ADR-003).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**", "/actuator", "/swagger-ui/**", "/v3/api-docs/**"
  };

  // every method that changes state; all of them are admin-only unless an earlier rule matched
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
                    // error dispatch has no auth (stateless, bearer filter runs once) => would
                    // turn 404/500 into 401
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(ApiPaths.EXPERIMENT_OWNER_CANDIDATES)
                    .hasAuthority(Permissions.ADMIN)
                    .requestMatchers(HttpMethod.PUT, ApiPaths.EXPERIMENT)
                    .hasAnyAuthority(Permissions.EXPERIMENT_WRITE, Permissions.ADMIN)
                    .requestMatchers(HttpMethod.POST, ApiPaths.EXPERIMENT_DOCUMENTS)
                    .hasAnyAuthority(Permissions.EXPERIMENT_WRITE, Permissions.ADMIN)
                    .requestMatchers(HttpMethod.DELETE, ApiPaths.EXPERIMENT_DOCUMENT)
                    .hasAnyAuthority(Permissions.EXPERIMENT_WRITE, Permissions.ADMIN)
                    .requestMatchers(HttpMethod.POST, ApiPaths.SENSOR_DOCUMENTS)
                    .hasAnyAuthority(Permissions.EXPERIMENT_WRITE, Permissions.ADMIN)
                    .requestMatchers(HttpMethod.DELETE, ApiPaths.SENSOR_DOCUMENT)
                    .hasAnyAuthority(Permissions.EXPERIMENT_WRITE, Permissions.ADMIN)
                    .requestMatchers(HttpMethod.POST, ApiPaths.FULCRUM_SYNC)
                    .authenticated()
                    .requestMatchers(WRITE_REQUESTS)
                    .hasAuthority(Permissions.ADMIN)
                    .anyRequest()
                    .authenticated())
        // bearer token on every request, session would only cost work (ADR-011 P4)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // browser sends no credential automatically (no cookie) => no csrf risk (ADR-011 P4)
        .csrf(AbstractHttpConfigurer::disable)
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

  // nobody reads them, default wraps every security filter of every request (ADR-011 P5)
  @Bean
  public SecurityObservationSettings securityObservationSettings() {
    return SecurityObservationSettings.noObservations();
  }
}
