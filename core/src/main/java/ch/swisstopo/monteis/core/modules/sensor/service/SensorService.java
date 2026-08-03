package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import org.springframework.stereotype.Service;

@Service
public class SensorService {
  private final SensorRepository repository;

  public SensorService(SensorRepository repository) {
    this.repository = repository;
  }

  @AuditChanges
  public Sensor createSensor(Sensor sensor) {
    return repository.create(sensor);
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    return repository.update(sensor);
  }
}
