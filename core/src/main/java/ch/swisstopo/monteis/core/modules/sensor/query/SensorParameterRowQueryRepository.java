package ch.swisstopo.monteis.core.modules.sensor.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterRowResponseDto;

/**
 * Read-flow contract for the sensor grid: one row per (Sensor, SensorParameter) pair, projected
 * straight from jOOQ - bypasses the Domain layer entirely, unlike {@code SensorRepository}, which
 * still owns the write flow's rich {@code Sensor} aggregate (nested parameters list, used for
 * create/update and the single-sensor read behind the edit dialog).
 */
public interface SensorParameterRowQueryRepository {
  PagedResult<SensorParameterRowResponseDto> findPaged(PagedRequest request);
}
