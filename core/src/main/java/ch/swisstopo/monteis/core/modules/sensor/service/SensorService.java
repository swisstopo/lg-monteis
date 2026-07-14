package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensorService {
  private final SensorRepository repository;

  public SensorService(SensorRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Sensor createSensor(Sensor sensor) {
    return repository.save(sensor);
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    return repository.update(sensor);
  }

  public List<Formula> findAllFormulas() {
    return repository.findAllFormulas();
  }
}
