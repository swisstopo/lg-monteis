package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**", "/actuator", "/swagger-ui/**", "/v3/api-docs/**"
  };

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) {
    http.authorizeHttpRequests(
            request ->
                request
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    // Per-experiment write check; the experiments_update RLS policy (V15) enforces
                    // the same rule in the database. Must precede the admin-only write rules below:
                    // first match wins.
                    .requestMatchers(HttpMethod.PUT, ExperimentWriteAuthorizationManager.PATH)
                    .access(new ExperimentWriteAuthorizationManager())
                    .requestMatchers(HttpMethod.POST)
                    .hasAuthority(ADMIN_AUTHORITY)
                    .requestMatchers(HttpMethod.PUT)
                    .hasAuthority(ADMIN_AUTHORITY)
                    .requestMatchers(HttpMethod.PATCH)
                    .hasAuthority(ADMIN_AUTHORITY)
                    .requestMatchers(HttpMethod.DELETE)
                    .hasAuthority(ADMIN_AUTHORITY)
                    .anyRequest()
                    .authenticated())
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
