package ch.swisstopo.monteis.core.modules.measurement.jooq;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MeasurementQueryRepository implements MeasurementQuery {

  @Override
  public List<ChartDataResponseDto> findMeasurements(
      List<Long> ids, OffsetDateTime from, OffsetDateTime to) {
    return List.of();
  }
}
