package ch.swisstopo.monteis.core.modules.measurement.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MeasurementService {

  private final MeasurementQuery query;

  public MeasurementService(MeasurementQuery query) {
    this.query = query;
  }

  public Optional<ChartDataResponseDto> findMeasurements(
      Long id, OffsetDateTime from, OffsetDateTime to) {
    if (from.isAfter(to)) {
      throw new ObjectBusinessValidationException(
          "measurement.dateRange.invalid", Map.of("from", from, "to", to));
    }
    return query.findMeasurements(id, from, to);
  }
}
