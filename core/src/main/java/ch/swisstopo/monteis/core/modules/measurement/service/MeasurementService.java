package ch.swisstopo.monteis.core.modules.measurement.service;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MeasurementService {
  private final MeasurementQuery query;

  public MeasurementService(MeasurementQuery query) {
    this.query = query;
  }

  public List<ChartDataResponseDto> findMeasurements(
      List<Long> ids, OffsetDateTime from, OffsetDateTime to) {
    // todo: implement guards here --> from/to guard
    return query.findMeasurements(ids, from, to);
  }
}
