package ch.swisstopo.monteis.core.modules.identity.web;

import static ch.swisstopo.monteis.core.infrastructure.security.Permissions.WRITE_ALL;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies {@link CurrentUserController} projects the caller's permissions (contract C4). */
@ControllerTest(CurrentUserController.class)
class CurrentUserControllerTest {

  @Autowired private MockMvc mockMvc;

  /** level, then the exact /api/me body (privilege matrix, last row). */
  static Stream<Arguments> levels() {
    return Stream.of(
        Arguments.of(PrivilegeLevel.BASISROLLE, body(false, "")),
        Arguments.of(PrivilegeLevel.EXPERIMENT_USER, body(false, "")),
        Arguments.of(PrivilegeLevel.EXPERIMENT_PI, body(false, "\"" + ASSIGNED_EXPERIMENT + "\"")),
        Arguments.of(PrivilegeLevel.MONTEIS_ADMIN, body(true, "")));
  }

  @ParameterizedTest
  @MethodSource("levels")
  void should_report_the_permissions_of_each_privilege_level(
      PrivilegeLevel level, String expectedBody) throws Exception {
    mockMvc
        .perform(get("/api/me").with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(content().json(expectedBody, JsonCompareMode.STRICT));
  }

  @Test
  void should_no_longer_report_can_write() throws Exception {
    mockMvc
        .perform(get("/api/me").with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(jsonPath("$.canWrite").doesNotExist());
  }

  @Test
  void should_report_nothing_for_a_token_that_did_not_pass_our_converter() throws Exception {
    // e.g. Spring Security Test's jwt() shortcut: authenticated, but not a
    // MonteisAuthenticationToken
    mockMvc
        .perform(get("/api/me").with(jwt().authorities(new SimpleGrantedAuthority(WRITE_ALL))))
        .andExpect(status().is5xxServerError())
        .andExpect(jsonPath("$.canWriteAll").doesNotExist());
  }

  @Test
  void should_reject_anonymous_callers() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  private static String body(boolean canWriteAll, String writeIds) {
    return "{\"canWriteAll\":%s,\"writeExperimentIds\":[%s]}".formatted(canWriteAll, writeIds);
  }
}
