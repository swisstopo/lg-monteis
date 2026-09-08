package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Repository for the {@link Sensor} aggregate root.
 * <p>
 * This interface works solely with rich domain objects, for both the state-mutating
 * (create, update) and the read-only operations - the sensor module does not project
 * straight into UI-optimized DTOs, so the {@code web} layer is responsible for
 * translating these domain objects into DTOs itself.
 */
public interface SensorRepository {
  /**
   * Persists a new {@link Sensor} entity.
   *
   * @param sensor the sensor to persist
   * @return the persisted sensor instance including DB managed state such as version
   */
  Sensor create(Sensor sensor);

  /**
   * Updates an existing {@link Sensor} entity.
   *
   * @param sensor the sensor to update
   * @return the updated sensor instance including DB managed state such as version
   */
  Sensor update(Sensor sensor);

  /**
   * Retrieves the domain {@link Sensor} for the given id, if it exists.
   *
   * @param id the sensor id
   * @return the sensor, or empty if no sensor with this id exists
   */
  Optional<Sensor> findById(UUID id);

  /**
   * Retrieves a page of sensors.
   *
   * @param request the requested page, together with an optional sort/filter model
   * @return the requested page of sensors together with the total row count
   */
  PagedResult<Sensor> findPaged(PagedRequest request);

  /**
   * Retrieves all formulas that are currently used by existing sensors.
   *
   * @return a list of all formulas alphabetically sorted by expression
   */
  List<Formula> findAllFormulas();

  /**
   * Retrieves all types that are currently used by existing sensors.
   *
   * @return a list of all types alphabetically sorted by name
   */
  List<SensorType> findAllTypes();

  /**
   * Retrieves all unaudited sensors
   *
   * @return a stream of all sensors which are not yet audited
   */
  Stream<Sensor> streamUnauditedSensors();
}
