package ch.swisstopo.monteis.core.modules.experiment.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQueryInterface;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentController.class)
class ExperimentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentQueryInterface queryRepository;

  @Test
  void should_route_get_experiment_details_and_return_json() throws Exception {
    // given
    FormulaResponseDto formula = new FormulaResponseDto(1L, "x * 2", 1);
    SensorResponseDto sensor =
        new SensorResponseDto(1L, "SENS-01", "Test Sensor", 0.0, 100.0, formula, 1);
    ReadExperimentDetailsDto expectedDto =
        new ReadExperimentDetailsDto(1L, "Experiment 1", "Description", 1, List.of(sensor));

    given(queryRepository.getExperimentDetails(1L)).willReturn(expectedDto);

    // when / then
    mockMvc
        .perform(
            get("/api/experiments/1/details").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedDto.id()))
        .andExpect(jsonPath("$.name").value(expectedDto.name()))
        .andExpect(jsonPath("$.description").value(expectedDto.description()))
        .andExpect(jsonPath("$.version").value(expectedDto.version()))
        .andExpect(jsonPath("$.sensors[0].id").value(sensor.id()))
        .andExpect(jsonPath("$.sensors[0].code").value(sensor.code()))
        .andExpect(jsonPath("$.sensors[0].formula.expression").value(formula.expression()));

    // Verify interaction
    then(queryRepository).should().getExperimentDetails(1L);
  }
}
