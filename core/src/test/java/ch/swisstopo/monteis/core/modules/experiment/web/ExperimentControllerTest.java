package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQuery;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentController.class)
class ExperimentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentQuery queryRepository;

  @MockitoBean private ExperimentService service;

  @MockitoBean private ExperimentWebMapper mapper;

  //  @Test
  //  void should_route_get_experiment_details_and_return_json() throws Exception {
  //    // given
  //    FormulaResponseDto formula = new FormulaResponseDto(1L, "x * 2", 1);
  //    SensorResponseDto sensor =
  //        new SensorResponseDto(
  //            1L,
  //            "SENS-01",
  //            "Test Sensor",
  //            Unit.METER,
  //            new SensorTypeResponseDto(1L, "Other", 1),
  //            "comment",
  //            new CoordinatesDto(0, 0, 0),
  //            new AlarmLimitsDto(0.0, 100.0),
  //            true,
  //            formula,
  //            1);
  //      ExperimentResponseDto expectedDto =
  //              new ExperimentResponseDto(1L, "Experiment 1", "Description");
  //
  //    given(queryRepository.getExperimentDetails(1L)).willReturn(expectedDto);
  //
  //    // when / then
  //    mockMvc
  //        .perform(
  //
  // get("/api/experiments/1/details").with(jwt()).contentType(MediaType.APPLICATION_JSON))
  //        .andExpect(status().isOk())
  //        .andExpect(jsonPath("$.id").value(expectedDto.id()))
  //        .andExpect(jsonPath("$.name").value(expectedDto.name()))
  //        .andExpect(jsonPath("$.description").value(expectedDto.description()))
  //        .andExpect(jsonPath("$.sensors[0].id").value(sensor.id()))
  //        .andExpect(jsonPath("$.sensors[0].code").value(sensor.code()))
  //        .andExpect(jsonPath("$.sensors[0].formula.expression").value(formula.expression()));
  //
  //    // Verify interaction
  //    then(queryRepository).should().getExperimentDetails(1L);
  //  }
}
