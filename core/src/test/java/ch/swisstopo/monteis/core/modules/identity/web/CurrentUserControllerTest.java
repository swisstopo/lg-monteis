package ch.swisstopo.monteis.core.modules.identity.web;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.WRITE_AUTHORITY;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies {@link CurrentUserController} reflects the caller's actually-granted authorities. */
@ControllerTest(CurrentUserController.class)
class CurrentUserControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void should_report_can_write_true_for_a_caller_with_write_authority() throws Exception {
    mockMvc
        .perform(
            get("/api/me")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority(READ_ALL_AUTHORITY),
                            new SimpleGrantedAuthority(WRITE_AUTHORITY))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(true));
  }

  @Test
  void should_report_can_write_false_for_a_read_only_caller() throws Exception {
    mockMvc
        .perform(get("/api/me").with(jwt().authorities(new SimpleGrantedAuthority(READ_AUTHORITY))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(false));
  }

  @Test
  void should_forbid_anonymous_callers() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}
