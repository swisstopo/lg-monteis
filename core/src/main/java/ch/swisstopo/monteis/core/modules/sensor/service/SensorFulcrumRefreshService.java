package ch.swisstopo.monteis.core.modules.sensor.service;

import static ch.swisstopo.monteis.core.modules.sensor.service.FulcrumCoordinatesMapper.coordinatesOf;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.FulcrumRefreshResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor.Reason;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.exception.DataChangedException;
import org.springframework.stereotype.Service;

@Service
public class SensorFulcrumRefreshService {

  // two writers: bulk timestamp needs no audit, coordinate change must pass the audit aspect
  private final SensorRepository repository;
  private final SensorService sensorService;
  private final FulcrumService fulcrumService;
  private final Clock clock;

  public SensorFulcrumRefreshService(
      SensorRepository repository,
      SensorService sensorService,
      FulcrumService fulcrumService,
      Clock clock) {
    this.repository = repository;
    this.sensorService = sensorService;
    this.fulcrumService = fulcrumService;
    this.clock = clock;
  }

  public FulcrumRefreshResult refreshSelected(Set<UUID> sensorIds) {
    List<Sensor> visible = repository.findAllByIds(sensorIds);
    return refresh(visible, notFoundAmong(sensorIds, visible));
  }

  public FulcrumRefreshResult refreshAllVisible() {
    return refresh(repository.findAllWithFulcrumId(), List.of());
  }

  private static List<SkippedSensor> notFoundAmong(Set<UUID> sensorIds, List<Sensor> visible) {
    Set<UUID> visibleIds = Set.copyOf(visible.stream().map(Sensor::getId).toList());
    return sensorIds.stream()
        .filter(id -> !visibleIds.contains(id))
        .map(SkippedSensor::notFound)
        .toList();
  }

  // no transaction around it: each save commits, a failed refresh keeps what it saved
  private FulcrumRefreshResult refresh(List<Sensor> sensors, List<SkippedSensor> notFound) {
    // read before any write: Fulcrum failing leaves every sensor as it was
    Map<UUID, FulcrumSensor> fulcrumSensorsById =
        fulcrumService.findSensorsByFulcrumId(fulcrumIdsOf(sensors));
    OffsetDateTime refreshedAt = OffsetDateTime.now(clock);
    Classification classification = classify(sensors, fulcrumSensorsById);

    repository.markFulcrumRefreshed(
        classification.unchanged().stream().map(Sensor::getId).toList(), refreshedAt);
    List<SkippedSensor> changedMeanwhile =
        saveSkippingConflicts(classification.changed(), refreshedAt);

    int updated = classification.changed().size() - changedMeanwhile.size();
    List<SkippedSensor> skipped =
        Stream.of(notFound, classification.skipped(), changedMeanwhile)
            .flatMap(List::stream)
            .toList();
    return new FulcrumRefreshResult(classification.unchanged().size() + updated, updated, skipped);
  }

  private static Set<UUID> fulcrumIdsOf(List<Sensor> sensors) {
    return sensors.stream()
        .filter(Sensor::hasFulcrumId)
        .map(Sensor::getFulcrumId)
        .collect(Collectors.toSet());
  }

  private static Classification classify(
      List<Sensor> sensors, Map<UUID, FulcrumSensor> fulcrumSensorsById) {
    List<SkippedSensor> skipped = new ArrayList<>();
    List<Sensor> unchanged = new ArrayList<>();
    List<ChangedSensor> changed = new ArrayList<>();
    for (Sensor sensor : sensors) {
      switch (compare(sensor, fulcrumSensorsById)) {
        case Comparison.WithoutFulcrumId() -> {}
        case Comparison.Skipped(Reason reason) -> skipped.add(SkippedSensor.of(sensor, reason));
        case Comparison.Unchanged() -> unchanged.add(sensor);
        case Comparison.Changed(Coordinates coordinates) ->
            changed.add(new ChangedSensor(sensor, coordinates));
      }
    }
    return new Classification(skipped, unchanged, changed);
  }

  private static Comparison compare(Sensor sensor, Map<UUID, FulcrumSensor> fulcrumSensorsById) {
    if (!sensor.hasFulcrumId()) {
      return new Comparison.WithoutFulcrumId();
    }
    return Optional.ofNullable(fulcrumSensorsById.get(sensor.getFulcrumId()))
        .map(fulcrumSensor -> compareCoordinates(sensor, fulcrumSensor))
        .orElseGet(() -> new Comparison.Skipped(Reason.NOT_IN_FULCRUM));
  }

  private static Comparison compareCoordinates(Sensor sensor, FulcrumSensor fulcrumSensor) {
    return coordinatesOf(fulcrumSensor)
        .<Comparison>map(
            coordinates ->
                coordinates.equals(sensor.getCoordinates())
                    ? new Comparison.Unchanged()
                    : new Comparison.Changed(coordinates))
        .orElseGet(() -> new Comparison.Skipped(Reason.COORDINATES_MISSING));
  }

  private List<SkippedSensor> saveSkippingConflicts(
      List<ChangedSensor> changed, OffsetDateTime refreshedAt) {
    List<SkippedSensor> changedMeanwhile = new ArrayList<>();
    for (ChangedSensor change : changed) {
      Sensor sensor = change.sensor();
      sensor.setCoordinates(change.coordinates());
      sensor.setLastFulcrumRefreshAt(refreshedAt);
      try {
        sensorService.saveRefreshedCoordinates(sensor);
      } catch (DataChangedException _) {
        // saved by someone else since it was read, one conflict must not block the others
        changedMeanwhile.add(SkippedSensor.of(sensor, Reason.CHANGED_MEANWHILE));
      }
    }
    return changedMeanwhile;
  }

  private sealed interface Comparison {
    record WithoutFulcrumId() implements Comparison {}

    record Skipped(Reason reason) implements Comparison {}

    record Unchanged() implements Comparison {}

    record Changed(Coordinates coordinates) implements Comparison {}
  }

  private record ChangedSensor(Sensor sensor, Coordinates coordinates) {}

  private record Classification(
      List<SkippedSensor> skipped, List<Sensor> unchanged, List<ChangedSensor> changed) {}
}
