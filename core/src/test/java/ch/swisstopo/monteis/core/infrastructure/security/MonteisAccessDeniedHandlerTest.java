package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.swisstopo.monteis.core.itconfig.LogCapture;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** The 403 body and the diagnostic log line of {@link MonteisAccessDeniedHandler}. */
class MonteisAccessDeniedHandlerTest {

  private static final String ACCESS_DENIED_BODY =
      "{\"target\":\"GLOBAL\",\"field\":null,\"actualValue\":null,"
          + "\"messageKey\":\"access.denied\",\"params\":{}}";

  private final MonteisAccessDeniedHandler handler =
      new MonteisAccessDeniedHandler(JsonMapper.builder().build());

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_write_403_with_the_access_denied_error_dto() throws IOException {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    handler.handle(
        new MockHttpServletRequest("PUT", "/api/experiments/x"),
        response,
        new AccessDeniedException("Access Denied"));

    // then
    assertEquals(403, response.getStatus());
    assertEquals("application/json", response.getContentType());
    assertEquals(ACCESS_DENIED_BODY, response.getContentAsString());
  }

  @Test
  void should_log_method_path_and_sub_at_warn_without_token_claims_or_ids() throws IOException {
    // given: an ExperimentPI whose principal carries experiment ids and a username
    var authentication = PrivilegeLevel.EXPERIMENT_PI.authentication();
    MonteisPrincipal principal = authentication.getPrincipal();
    SecurityContextHolder.getContext().setAuthentication(authentication);
    String path = "/api/experiments/" + PrivilegeLevel.OTHER_EXPERIMENT;

    try (LogCapture logs = LogCapture.of(MonteisAccessDeniedHandler.class, Level.WARN)) {
      // when
      handler.handle(
          new MockHttpServletRequest("PUT", path),
          new MockHttpServletResponse(),
          new AccessDeniedException("Access Denied"));

      // then: exactly one line with method, path and sub, and nothing from the token
      assertEquals(1, logs.messages().size());
      String line = logs.messages().getFirst();
      assertTrue(line.contains("PUT " + path), line);
      assertTrue(line.contains(principal.getSubject().toString()), line);
      assertFalse(line.contains(principal.getName()), line);
      assertFalse(line.contains(PrivilegeLevel.ASSIGNED_EXPERIMENT.toString()), line);
      assertFalse(line.contains("api:"), line);
    }
  }

  @Test
  void should_log_anonymous_when_no_monteis_principal_is_bound() throws IOException {
    try (LogCapture logs = LogCapture.of(MonteisAccessDeniedHandler.class, Level.WARN)) {
      handler.handle(
          new MockHttpServletRequest("POST", "/api/experiments"),
          new MockHttpServletResponse(),
          new AccessDeniedException("Access Denied"));

      assertTrue(logs.messages().getFirst().endsWith("for sub anonymous"));
    }
  }

  @Test
  void should_not_log_when_warn_is_off() throws IOException {
    try (LogCapture logs = LogCapture.of(MonteisAccessDeniedHandler.class, Level.ERROR)) {
      handler.handle(
          new MockHttpServletRequest("POST", "/api/experiments/" + UUID.randomUUID()),
          new MockHttpServletResponse(),
          new AccessDeniedException("Access Denied"));

      assertTrue(logs.messages().isEmpty());
    }
  }
}
