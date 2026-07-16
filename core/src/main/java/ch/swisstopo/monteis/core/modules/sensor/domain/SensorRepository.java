package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.stream.Stream;

/**
 * Command-side repository for the {@link Sensor} aggregate root.
 * <p>
 * This interface is part of the strict Domain-Driven Design (DDD) write flow.
 * It is exclusively responsible for state-mutating operations (e.g., create, update)
 * and domain reconstruction. It works solely with rich domain objects to ensure
 * business invariants are protected.
 * <p>
 * Do not add UI-specific read methods here. For read-only operations that return
 * DTOs, see {@link ch.swisstopo.monteis.core.modules.sensor.query.SensorQuery}.
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
   * Retrieves all unaudited sensors
   *
   * @return a stream of all sensors which are not yet audited
   */
  Stream<Sensor> streamUnauditedSensors();
}
