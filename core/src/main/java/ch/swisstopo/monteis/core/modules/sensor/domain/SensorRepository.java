package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.List;

public interface SensorRepository {

  /**
   * Persists a new {@link Sensor} entity.
   *
   * @param sensor the sensor to persist
   * @return the persisted sensor instance including DB managed state such as version
   */
  Sensor save(Sensor sensor);

  /**
   * Retrieves all formulas that are currently used by existing sensors.
   *
   * @return a list of all formulas alphabetically sorted
   */
  List<Formula> findAllFormulas();
}
