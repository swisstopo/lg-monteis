package ch.swisstopo.monteis.core.modules.overview.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(OverviewController.class)
class OverviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @SuppressWarnings("unused")
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private OverviewService overviewService;

  @Test
  void should_route_get_metrics_and_verify_output() throws Exception {
    // given: Define input parameters and expected output DTOs
    int limit = 5;
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-15T10:00:05Z");

    ReadSimpleMetricDto expectedDto =
        new ReadSimpleMetricDto(timestamp, "SENS-01", 25.4, 0.98, (short) 1, "ACTIVE");

    List<ReadSimpleMetricDto> expectedResponse = List.of(expectedDto);

    given(overviewService.fetchRecentMetrics(limit)).willReturn(expectedResponse);

    // when / then: Perform request and assert the actual JSON array fields match our expected
    // output
    mockMvc
        .perform(
            get("/api/overview/metrics")
                .with(jwt())
                .param("limit", String.valueOf(limit))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size()").value(expectedResponse.size()))
        .andExpect(jsonPath("$[0].timestamp").value(expectedDto.timestamp().toString()))
        .andExpect(jsonPath("$[0].sensorId").value(expectedDto.sensorId()))
        .andExpect(jsonPath("$[0].rawValue").value(expectedDto.rawValue()))
        .andExpect(jsonPath("$[0].normValue").value(expectedDto.normValue()))
        .andExpect(jsonPath("$[0].version").value(Integer.valueOf(expectedDto.version())))
        .andExpect(jsonPath("$[0].status").value(expectedDto.status()));

    // Verify interaction sequence
    then(overviewService).should().fetchRecentMetrics(limit);
    then(overviewService).shouldHaveNoMoreInteractions();
  }
}
