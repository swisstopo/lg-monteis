package ch.swisstopo.monteis.core.modules.identity.web;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies {@link CurrentUserController} reflects the caller's actually-granted authorities. */
@ControllerTest(CurrentUserController.class)
class CurrentUserControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void should_report_can_write_true_for_an_admin() throws Exception {
    mockMvc
        .perform(
            get("/api/me").with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(true))
        .andExpect(jsonPath("$.canWriteAllExperiments").value(true));
  }

  @Test
  void should_report_can_write_false_for_a_read_only_caller() throws Exception {
    mockMvc
        .perform(
            get("/api/me")
                .with(jwt().authorities(new SimpleGrantedAuthority(EXPERIMENT_READ_AUTHORITY))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(false))
        .andExpect(jsonPath("$.canWriteAllExperiments").value(false));
  }

  @Test
  void should_report_can_write_all_experiments_for_experiment_write_all_without_admin()
      throws Exception {
    mockMvc
        .perform(
            get("/api/me")
                .with(
                    jwt().authorities(new SimpleGrantedAuthority(EXPERIMENT_WRITE_ALL_AUTHORITY))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(false))
        .andExpect(jsonPath("$.canWriteAllExperiments").value(true));
  }

  @Test
  void should_report_the_callers_scoped_write_experiment_ids() throws Exception {
    UUID writableExperimentId = UUID.randomUUID();
    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.randomUUID(),
            "scoped-writer",
            List.of(writableExperimentId),
            List.of(writableExperimentId));
    var authentication =
        new MonteisAuthenticationToken(
            null, principal, List.of(new SimpleGrantedAuthority(EXPERIMENT_WRITE_AUTHORITY)));

    mockMvc
        .perform(get("/api/me").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canWrite").value(false))
        .andExpect(jsonPath("$.canWriteAllExperiments").value(false))
        .andExpect(jsonPath("$.writeExperimentIds[0]").value(writableExperimentId.toString()));
  }

  @Test
  void should_forbid_anonymous_callers() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}
