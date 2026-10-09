package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.UNASSIGNED_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end verification of {@link SecurityConfig}'s request rules (BR4.8) for all five privilege
 * levels (NFR1.2): a real {@code Authorization: Bearer} header, decoded by the (mocked) {@link
 * JwtDecoder}, run through the actually-configured {@link MonteisJwtAuthenticationConverter} bean
 * and {@link Capabilities} - not Spring Security Test's {@code jwt()} shortcut, which bypasses that
 * wiring entirely. The dummy controller mirrors the real paths, so these are the rules as matched.
 */
@ControllerTest
@ContextConfiguration(classes = {SecurityConfigAuthorizationTest.DummyController.class})
class SecurityConfigAuthorizationTest {

  private static final String ACCESS_DENIED_BODY =
      "{\"target\":\"GLOBAL\",\"field\":null,\"actualValue\":null,"
          + "\"messageKey\":\"access.denied\",\"params\":{}}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  /** method, path, then the levels allowed (all others get 403). */
  static Stream<Arguments> rules() {
    PrivilegeLevel[] adminOnly = {PrivilegeLevel.MONTEIS_ADMIN};
    PrivilegeLevel[] everyone = PrivilegeLevel.values();
    PrivilegeLevel[] allExperiments = {PrivilegeLevel.GLOBAL_EDITOR, PrivilegeLevel.MONTEIS_ADMIN};
    String sensor = "/api/sensors/" + UUID.randomUUID();
    String organisation = "/api/organisations/" + UUID.randomUUID();
    return Stream.of(
        Arguments.of(HttpMethod.GET, "/api/experiments", everyone),
        Arguments.of(HttpMethod.GET, "/api/experiments/" + UNASSIGNED_EXPERIMENT, everyone),
        Arguments.of(
            HttpMethod.PUT,
            "/api/experiments/" + ASSIGNED_EXPERIMENT,
            new PrivilegeLevel[] {
              PrivilegeLevel.EXPERIMENT_PI,
              PrivilegeLevel.GLOBAL_EDITOR,
              PrivilegeLevel.MONTEIS_ADMIN
            }),
        Arguments.of(HttpMethod.PUT, "/api/experiments/" + OTHER_EXPERIMENT, allExperiments),
        Arguments.of(HttpMethod.PUT, "/api/experiments/" + UNASSIGNED_EXPERIMENT, allExperiments),
        Arguments.of(HttpMethod.PUT, "/api/experiments/not-a-uuid", new PrivilegeLevel[] {}),
        Arguments.of(HttpMethod.POST, "/api/experiments", adminOnly),
        Arguments.of(HttpMethod.GET, "/api/sensors", everyone),
        Arguments.of(HttpMethod.GET, sensor, everyone),
        Arguments.of(HttpMethod.POST, "/api/sensors", adminOnly),
        Arguments.of(HttpMethod.PUT, sensor, adminOnly),
        Arguments.of(HttpMethod.GET, "/api/organisations", everyone),
        Arguments.of(HttpMethod.GET, "/api/organisations/all", everyone),
        Arguments.of(HttpMethod.GET, "/api/organisations/csv", everyone),
        Arguments.of(HttpMethod.GET, organisation, everyone),
        Arguments.of(HttpMethod.POST, "/api/organisations", adminOnly),
        Arguments.of(HttpMethod.PUT, organisation, adminOnly),
        Arguments.of(HttpMethod.POST, "/api/other", adminOnly),
        Arguments.of(HttpMethod.PUT, "/api/other", adminOnly),
        Arguments.of(HttpMethod.PATCH, "/api/other", adminOnly),
        Arguments.of(HttpMethod.DELETE, "/api/other", adminOnly),
        Arguments.of(HttpMethod.GET, "/api/measurements", everyone),
        Arguments.of(HttpMethod.GET, "/api/me", everyone));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("rules")
  void should_apply_the_request_rule_to_every_privilege_level(
      HttpMethod method, String path, PrivilegeLevel[] allowed) throws Exception {
    for (PrivilegeLevel level : PrivilegeLevel.values()) {
      String token = givenDecodableToken(level);

      var result = mockMvc.perform(withBearer(request(method, path), token));

      if (List.of(allowed).contains(level)) {
        result.andExpect(status().isOk());
      } else {
        result
            .andExpect(status().isForbidden())
            .andExpect(content().json(ACCESS_DENIED_BODY, JsonCompareMode.STRICT));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_leave_public_endpoints_open(PrivilegeLevel level) throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc
        .perform(withBearer(get("/actuator/health"), givenDecodableToken(level)))
        .andExpect(status().isOk());
  }

  @Test
  void should_require_authentication_for_reads() throws Exception {
    mockMvc.perform(get("/api/experiments")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void should_forbid_anonymous_writes() throws Exception {
    mockMvc
        .perform(put("/api/experiments/{id}", ASSIGNED_EXPERIMENT))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/experiments")).andExpect(status().is4xxClientError());
  }

  @Test
  void should_reject_a_token_with_experiment_write_but_without_experiment_read() throws Exception {
    givenDecodableToken(
        "misconfigured-token", Map.of("roles", List.of("monteis-client:experiment:write")));

    mockMvc
        .perform(withBearer(get("/api/experiments"), "misconfigured-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_grant_nothing_to_a_token_with_only_legacy_roles() throws Exception {
    givenDecodableToken(
        "legacy-token",
        Map.of("roles", List.of("monteis-client:read-all", "monteis-client:write")));

    mockMvc
        .perform(withBearer(post("/api/experiments"), "legacy-token"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(withBearer(put("/api/experiments/{id}", ASSIGNED_EXPERIMENT), "legacy-token"))
        .andExpect(status().isForbidden());
  }

  private static RequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private String givenDecodableToken(PrivilegeLevel level) {
    String token = level.name().toLowerCase() + "-token";
    given(jwtDecoder.decode(token)).willReturn(level.jwt(token));
    return token;
  }

  private void givenDecodableToken(String token, Map<String, Object> monteisAccess) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("monteis_access", monteisAccess)
            .claim("preferred_username", "test_user_name")
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    given(jwtDecoder.decode(token)).willReturn(jwt);
  }

  /**
   * A fake controller used exclusively by this test class to trigger the security rules at the
   * real paths. Keeps these authorization contract tests decoupled from real business controllers.
   */
  @RestController
  static class DummyController {

    @GetMapping({
      "/api/experiments",
      "/api/measurements",
      "/api/me",
      "/api/organisations",
      "/api/sensors"
    })
    public String read() {
      return "ok";
    }

    @GetMapping({"/api/experiments/{id}", "/api/organisations/{id}", "/api/sensors/{id}"})
    public String readOne(@PathVariable String id) {
      return "ok";
    }

    @PutMapping({"/api/experiments/{id}", "/api/organisations/{id}", "/api/sensors/{id}"})
    public String update(@PathVariable String id) {
      return "ok";
    }

    @PostMapping({"/api/experiments", "/api/organisations", "/api/sensors", "/api/other"})
    public String create() {
      return "ok";
    }

    @PutMapping("/api/other")
    public String put() {
      return "ok";
    }

    @PatchMapping("/api/other")
    public String patch() {
      return "ok";
    }

    @DeleteMapping("/api/other")
    public String delete() {
      return "ok";
    }

    @GetMapping("/actuator/health")
    public String health() {
      return "UP";
    }
  }
}
