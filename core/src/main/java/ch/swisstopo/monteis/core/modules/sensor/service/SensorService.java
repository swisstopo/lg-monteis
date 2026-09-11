package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    for (SensorParameter parameter : created.getParameters()) {
      configPublisher.publish(created, parameter);
    }
    return created;
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    Sensor before = repository.findById(sensor.getId()).orElse(null);
    Sensor updated = repository.update(sensor);

    Map<UUID, SensorParameter> parametersBefore =
        before == null
            ? Map.of()
            : before.getParameters().stream()
                .collect(Collectors.toMap(SensorParameter::getId, p -> p));

    for (SensorParameter parameter : updated.getParameters()) {
      SensorParameter parameterBefore = parametersBefore.get(parameter.getId());
      if (parameter.changeTriggersPublish(parameterBefore)) {
        configPublisher.publish(updated, parameter);
      }
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

  /**
   * Republishes the config for every sensor parameter, unconditionally (bypassing {@link
   * SensorParameter#changeTriggersPublish}). One-time operational tool for MON-143's rollout: readings
   * ingested before a parameter's config was ever known are written under the cache's default (version
   * 0) config, so simply re-sending each parameter's current config - even unchanged - is enough to
   * trigger the pipeline's existing reprocessing flow and backfill {@code sensor_parameter_id} on them
   * (every real parameter starts at version 1). See the read-path queries that now depend on that id
   * being populated.
   */
  public void republishAllActiveParameterConfigs() {
    try (var sensors = repository.streamAllSensors()) {
      sensors.forEach(
          sensor -> sensor.getParameters().forEach(p -> configPublisher.publish(sensor, p)));
    }
  }
}
