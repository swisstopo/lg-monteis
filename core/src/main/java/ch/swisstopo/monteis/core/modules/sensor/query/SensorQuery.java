package ch.swisstopo.monteis.core.modules.sensor.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import java.util.List;
import java.util.UUID;

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
   * Retrieves a sensor by its ID, projected straight into a {@link SensorResponseDto}.
   *
   * @param id the ID of the sensor to retrieve
   * @return the sensor response DTO
   */
  SensorResponseDto getById(UUID id);

  /**
   * Retrieves a page of sensors, projected straight into {@link SensorResponseDto}s.
   *
   * @param request the requested page, together with an optional sort/filter model
   * @return the requested page of sensors together with the total row count
   */
  PagedResult<SensorResponseDto> getSensors(PagedRequest request);

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
