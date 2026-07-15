package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.List;
import java.util.stream.Stream;

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
   * Retrieves all formulas that are currently used by existing sensors.
   *
   * @return a list of all formulas alphabetically sorted
   */
  List<Formula> findAllFormulas();

  /**
   * Retrieves all unaudited sensors
   *
   * @return a stream of all sensors which are not yet audited
   */
  Stream<Sensor> streamUnauditedSensors();
}
