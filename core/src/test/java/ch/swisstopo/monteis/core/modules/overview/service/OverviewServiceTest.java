package ch.swisstopo.monteis.core.modules.overview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.overview.jooq.OverviewRepository;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverviewServiceTest {

  @Mock private OverviewRepository repository;

  @InjectMocks private OverviewService service;

  @Test
  void should_delegate_fetch_recent_metrics_to_repository_when_data_exists() {
    // given
    int limit = 5;
    ReadSimpleMetricDto mockMetric = mock(ReadSimpleMetricDto.class);

    List<ReadSimpleMetricDto> expectedResults = List.of(mockMetric);

    given(repository.fetchRecentMetrics(limit)).willReturn(expectedResults);

    // when
    List<ReadSimpleMetricDto> actualResults = service.fetchRecentMetrics(limit);

    // then
    then(repository).should().fetchRecentMetrics(limit);
    assertEquals(expectedResults, actualResults);
    assertEquals(1, actualResults.size());
  }

  @Test
  void should_handle_empty_metrics_result_gracefully() {
    // given
    int limit = 5;
    List<ReadSimpleMetricDto> expectedResults = List.of(); // Empty list

    given(repository.fetchRecentMetrics(limit)).willReturn(expectedResults);

    // when
    List<ReadSimpleMetricDto> actualResults = service.fetchRecentMetrics(limit);

    // then
    then(repository).should().fetchRecentMetrics(limit);
    assertTrue(actualResults.isEmpty());
    assertEquals(expectedResults, actualResults);
  }
}
