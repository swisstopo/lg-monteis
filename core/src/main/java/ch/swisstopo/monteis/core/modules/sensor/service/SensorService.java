package ch.swisstopo.monteis.core.modules.sensor.service;

import static ch.swisstopo.monteis.core.modules.sensor.service.FulcrumCoordinatesMapper.coordinatesOf;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SensorService {
  private final SensorRepository repository;
  private final SensorConfigPublisher configPublisher;
  private final FulcrumService fulcrumService;
  private final Clock clock;

  public SensorService(
      SensorRepository repository,
      SensorConfigPublisher configPublisher,
      FulcrumService fulcrumService,
      Clock clock) {
    this.repository = repository;
    this.configPublisher = configPublisher;
    this.fulcrumService = fulcrumService;
    this.clock = clock;
  }

  @AuditChanges
  public Sensor createSensor(Sensor sensor) {
    takeCoordinatesFromFulcrum(sensor);

    Sensor created = repository.create(sensor);
    for (SensorParameter parameter : created.getParameters()) {
      configPublisher.publish(created, parameter);
    }
    return created;
  }

  @AuditChanges
  public Sensor updateSensor(Sensor sensor) {
    Sensor before = repository.findById(sensor.getId()).orElse(null);

    takeCoordinatesFromFulcrum(sensor);
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

  private void takeCoordinatesFromFulcrum(Sensor sensor) {
    if (!sensor.hasFulcrumId()) {
      sensor.setLastFulcrumRefreshAt(null);
      return;
    }
    sensor.setCoordinates(findCoordinatesInFulcrumOrFail(sensor.getFulcrumId()));
    sensor.setLastFulcrumRefreshAt(OffsetDateTime.now(clock));
  }

  private Coordinates findCoordinatesInFulcrumOrFail(UUID fulcrumId) {
    FulcrumSensor fulcrumSensor = findInFulcrumOrFail(fulcrumId);
    return coordinatesOf(fulcrumSensor)
        .orElseThrow(() -> coordinatesMissing(fulcrumId, fulcrumSensor));
  }

  private FulcrumSensor findInFulcrumOrFail(UUID fulcrumId) {
    return fulcrumService
        .findSensorByFulcrumId(fulcrumId)
        .orElseThrow(
            () -> new ObjectBusinessValidationException("fulcrum.sensor.not-found", Map.of()));
  }

  private static ObjectBusinessValidationException coordinatesMissing(
      UUID fulcrumId, FulcrumSensor fulcrumSensor) {
    return new ObjectBusinessValidationException(
        "sensor.fulcrum.coordinatesMissing",
        Map.of(
            "fulcrumId", fulcrumId.toString(),
            "missing", String.join(", ", fulcrumSensor.missingCoordinateAxes())));
  }

  @AuditChanges
  public Sensor saveRefreshedCoordinates(Sensor sensor) {
    return repository.updateRefreshedCoordinates(sensor);
  }

  public Sensor getSensor(UUID id) {
    return repository
        .findById(id)
        // RLS hides sensors outside the caller's experiments, so hidden and missing look the same
        .orElseThrow(() -> new ObjectNotFoundException(Sensor.class));
  }

  public boolean canWriteAnExperimentOf(UUID id) {
    return repository.canWriteAnExperimentOf(id);
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
