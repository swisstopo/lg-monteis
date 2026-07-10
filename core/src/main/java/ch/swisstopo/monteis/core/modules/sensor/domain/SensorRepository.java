package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.List;

public interface SensorRepository {
  Sensor save(Sensor sensor);

  List<Formula> findAllFormulas();
}
