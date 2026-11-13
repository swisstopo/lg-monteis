package ch.swisstopo.monteis.core.modules.experiment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IT
@AutoConfigureMockMvc
class ExperimentCsvExportIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void should_export_only_the_experiments_the_caller_may_read() throws Exception {
    // when
    MvcResult started =
        mockMvc
            .perform(
                get("/api/experiments/csv")
                    .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
            .andExpect(request().asyncStarted())
            .andReturn();
    String csv =
        mockMvc
            .perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // then
    assertThat(csv)
        .contains("Mont Terri Alpha", "Mont Terri Gamma")
        .doesNotContain("Mont Terri Beta");
  }
}
