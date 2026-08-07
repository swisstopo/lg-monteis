package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQuery;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentController.class)
class ExperimentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentQuery queryRepository;

  @MockitoBean private ExperimentService service;

  @MockitoBean private ExperimentWebMapper mapper;

  @Test
  void should_route_get_experiment_details_and_return_json() throws Exception {
    // given

    // when / then

    // Verify interaction

  }
}
