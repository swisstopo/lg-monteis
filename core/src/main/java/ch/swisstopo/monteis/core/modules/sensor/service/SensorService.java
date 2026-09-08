package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  public Sensor getSensor(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ObjectBusinessValidationException("object.deleted", Map.of()));
  }

  public PagedResult<Sensor> getSensors(PagedRequest request) {
    return repository.findPaged(request);
  }

  public List<Formula> findAllFormulas() {
    return repository.findAllFormulas();
  }

  public List<SensorType> findAllTypes() {
    return repository.findAllTypes();
  }
}
