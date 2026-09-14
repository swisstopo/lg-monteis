package ch.swisstopo.monteis.core.modules.measurement.query;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
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

  List<MeasurementResponseDto> findMeasurements();
}
