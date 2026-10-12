package ch.swisstopo.monteis.core.modules.measurement.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailResponseDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementQuery {

  /**
   * Fetches one sensor's measurements at full resolution for the given range.
   *
   * @return the chart series, or empty when the sensor does not exist or is not visible to the
   *     caller under row-level security
   */
  Optional<ChartDataResponseDto> findChartData(UUID id, OffsetDateTime from, OffsetDateTime to);

  PagedResult<MeasurementResponseDto> findPaged(PagedRequest request);

  /**
   * Fetches a sensor with all its parameters and the newest reading of each, in a single round
   * trip.
   *
   * @return the sensor detail, or empty when the sensor does not exist or is not visible to the
   *     caller under row-level security
   */
  Optional<SensorDetailResponseDto> findSensorDetail(UUID id, LocalDate today);
}
