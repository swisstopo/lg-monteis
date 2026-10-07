package ch.swisstopo.monteis.core.modules.sensor.query;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailResponseDto;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-flow contract for the sensor detail view: a sensor with all its parameters and the newest
 * reading of each, projected straight from jOOQ in a single round trip - bypasses the Domain layer
 * like {@link SensorParameterRowQueryRepository}.
 */
public interface SensorDetailQueryRepository {

  /**
   * @param id the sensor id
   * @param today reference date for the main experiment's status
   * @return the sensor detail, or empty if no sensor with this id exists
   */
  Optional<SensorDetailResponseDto> findById(UUID id, LocalDate today);
}
