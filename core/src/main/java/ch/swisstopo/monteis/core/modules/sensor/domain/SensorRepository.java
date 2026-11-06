package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import java.time.OffsetDateTime;
import java.util.Collection;
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
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     sensor does not exist or is hidden from the caller
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
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     sensor does not exist or is hidden from the caller
   */
  void requireVisible(UUID id);

  /** Whether the caller may upload documents to the sensor, see {@code V22}. */
  boolean canWriteAnExperimentOf(UUID id);

  /**
   * Retrieves the sensors among the given ids the caller can see, including their parameters.
   *
   * @param ids the sensor ids
   * @return the visible sensors, missing and hidden ids are left out
   */
  List<Sensor> findAllByIds(Collection<UUID> ids);

  List<Sensor> findAllWithFulcrumId();

  /**
   * Writes the sensor's coordinates and last Fulcrum refresh, leaving every other field as stored.
   *
   * @param sensor the sensor carrying the refreshed values and the version they were read at
   * @return the sensor with its incremented version
   * @throws org.jooq.exception.DataChangedException if the sensor was changed or deleted meanwhile
   */
  Sensor updateRefreshedCoordinates(Sensor sensor);

  /**
   * Sets the last Fulcrum refresh of sensors whose coordinates did not change, without counting
   * it as a change of the sensor: the version stays. Sensors whose Fulcrum ID was removed
   * meanwhile are left without a last Fulcrum refresh.
   *
   * @param sensorIds the refreshed sensors
   * @param refreshedAt when their coordinates were read from Fulcrum
   */
  void markFulcrumRefreshed(Collection<UUID> sensorIds, OffsetDateTime refreshedAt);

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

  /**
   * Retrieves every sensor, including its parameters.
   *
   * @return a stream of all sensors
   */
  Stream<Sensor> streamAllSensors();
}
