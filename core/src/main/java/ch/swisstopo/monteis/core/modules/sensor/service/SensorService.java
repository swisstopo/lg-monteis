package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import org.springframework.stereotype.Service;

@Service
public class SensorService {
  private final SensorRepository repository;
  private final SensorConfigPublisher configPublisher;

  public SensorService(SensorRepository repository, SensorConfigPublisher configPublisher) {
    this.repository = repository;
    this.configPublisher = configPublisher;
  }

  @AuditChanges
  public Sensor createSensor(Sensor sensor) {
    Sensor created = repository.create(sensor);
    configPublisher.publish(created); // a brand-new sensor always needs its config announced
    return created;
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    Sensor before = repository.findById(sensor.getId()).orElse(null);
    Sensor updated = repository.update(sensor);
    if (updated.changeTriggersPublish(before)) {
      configPublisher.publish(updated);
    }
    return updated;
  }
}
