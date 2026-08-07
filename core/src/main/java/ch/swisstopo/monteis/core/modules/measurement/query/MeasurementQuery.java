package ch.swisstopo.monteis.core.modules.measurement.query;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;

public interface MeasurementQuery {

  /**
   * Fetches all relevant measurements according to its params
   * @return all information needed for rendering as ChartDataResponseDto
   */
  List<ChartDataResponseDto> findMeasurements(
      List<Long> ids, OffsetDateTime from, OffsetDateTime to);
}
