package ch.swisstopo.monteis.core.infrastructure.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end verification of {@link SecurityConfig}'s HTTP authorization rules: a real {@code
 * Authorization: Bearer} header, decoded by the (mocked) {@link JwtDecoder}, run through the
 * actually-configured {@link MonteisJwtAuthenticationConverter} bean — not Spring Security Test's
 * {@code jwt().authorities(...)} shortcut, which bypasses that wiring entirely.
 */
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfigAuthorizationTest.DummyController.class})
@Import(SecurityConfig.class)
class SecurityConfigAuthorizationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void should_allow_admin_to_write() throws Exception {
    givenDecodableToken("admin-token", "admin");

    mockMvc
        .perform(post("/dummy/write").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk());
  }

  @Test
  void should_forbid_user_from_writing() throws Exception {
    givenDecodableToken("user-token", "user");

    mockMvc
        .perform(post("/dummy/write").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void should_forbid_anonymous_from_writing() throws Exception {
    mockMvc.perform(post("/dummy/write")).andExpect(status().isForbidden());
  }

  @Test
  void should_allow_admin_to_read() throws Exception {
    givenDecodableToken("admin-token", "admin");

    mockMvc
        .perform(get("/dummy/read").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk());
  }

  @Test
  void should_allow_user_to_read() throws Exception {
    givenDecodableToken("user-token", "user");

    mockMvc
        .perform(get("/dummy/read").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andExpect(status().isOk());
  }

  @Test
  void should_forbid_anonymous_from_reading() throws Exception {
    mockMvc.perform(get("/dummy/read")).andExpect(status().isUnauthorized());
  }

  @Test
  void should_allow_public_endpoint_without_authentication() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  private void givenDecodableToken(String token, String realmRole) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("realm_access", Map.of("roles", List.of(realmRole)))
            .claim("preferred_username", "asdf")
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    given(jwtDecoder.decode(token)).willReturn(jwt);
  }

  /**
   * A fake controller used exclusively by this test class to trigger the security rules. Keeps
   * these authorization contract tests decoupled from real business controllers.
   */
  @RestController
  static class DummyController {

    @GetMapping("/dummy/read")
    public String read() {
      return "ok";
    }

    @PostMapping("/dummy/write")
    public String write() {
      return "ok";
    }

    @GetMapping("/actuator/health")
    public String health() {
      return "UP";
    }
  }
}
