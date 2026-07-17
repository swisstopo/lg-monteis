package ch.swisstopo.monteis.core.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    SecurityContextFilter securityContextFilter = new SecurityContextFilter();

    http.csrf(AbstractHttpConfigurer::disable)
        // POC-only: MockAuthenticationFilter unconditionally attaches a statically mocked JWT
        // principal to every request instead of real Keycloak auth (see its Javadoc for how to
        // swap it out once Keycloak is wired up). BearerTokenAuthenticationFilter.class is used
        // purely as a well-known ordering anchor and isn't itself part of this chain.
        .authorizeHttpRequests(request -> request.anyRequest().permitAll())
        .addFilterAfter(securityContextFilter, BearerTokenAuthenticationFilter.class)
        .addFilterBefore(new MockAuthenticationFilter(), SecurityContextFilter.class);

    return http.build();
  }
}
