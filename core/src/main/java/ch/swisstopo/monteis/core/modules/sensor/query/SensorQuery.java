package ch.swisstopo.monteis.core.modules.sensor.query;

import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import java.util.List;

/**
 * Query-side interface for Sensor-related read operations.
 * <p>
 * This interface implements the CQRS read flow. It explicitly bypasses the
 * domain model and MapStruct mappers, using jOOQ to project database records
 * directly into UI-optimized Data Transfer Objects (DTOs).
 * <p>
 * Use this interface for HTTP GET endpoints, dashboards, or any data aggregations
 * where business invariant validation is not required.
 */
public interface SensorQuery {

  /**
   * Retrieves a {@link Sensor} entity by its ID.
   *
   * @param id the ID of the sensor to retrieve
   * @return the sensor instance
   */
  Sensor get(Long id);

  /**
   * Retrieves all formulas that are currently used by existing sensors.
   *
   * @return a list of all formulas alphabetically sorted
   */
  List<FormulaResponseDto> findAllFormulas();

  /**
   * Retrieves all types that are currently used by existing sensors.
   *
   * @return a list of all types alphabetically sorted
   */
  List<SensorTypeResponseDto> findAllTypes();
}
