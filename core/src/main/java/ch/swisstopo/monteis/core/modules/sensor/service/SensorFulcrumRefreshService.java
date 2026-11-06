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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jooq.exception.DataChangedException;
import org.springframework.stereotype.Service;

/** Takes the current coordinates from Fulcrum over into the selected sensors (MON-173). */
@Service
public class SensorFulcrumRefreshService {

  private final SensorRepository repository;
  private final FulcrumService fulcrumService;
  private final SensorService sensorService;
  private final Clock clock;

  public SensorFulcrumRefreshService(
      SensorRepository repository,
      FulcrumService fulcrumService,
      SensorService sensorService,
      Clock clock) {
    this.repository = repository;
    this.fulcrumService = fulcrumService;
    this.sensorService = sensorService;
    this.clock = clock;
  }

  public FulcrumRefreshResult refresh(Set<UUID> sensorIds) {
    List<Sensor> sensors = repository.findAllByIds(sensorIds);
    // read before any write: Fulcrum failing leaves every sensor as it was
    Map<UUID, FulcrumSensor> fulcrumSensorsById = fulcrumService.findAllSensorsByFulcrumId();
    OffsetDateTime refreshedAt = OffsetDateTime.now(clock);

    List<SkippedSensor> skipped = new ArrayList<>(notFoundAmong(sensorIds, sensors));
    List<Sensor> unchanged = new ArrayList<>();
    List<Sensor> changed = new ArrayList<>();
    for (Sensor sensor : sensors) {
      switch (compare(sensor, fulcrumSensorsById)) {
        case Comparison.Skipped(Reason reason) ->
            skipped.add(new SkippedSensor(sensor.getId(), reason));
        case Comparison.Unchanged() -> unchanged.add(sensor);
        case Comparison.Changed(Coordinates coordinates) -> {
          sensor.setCoordinates(coordinates);
          changed.add(sensor);
        }
      }
    }

    repository.markFulcrumRefreshed(idsOf(unchanged), refreshedAt);
    List<SkippedSensor> notSaved = saveChanged(changed, refreshedAt);
    skipped.addAll(notSaved);

    int updated = changed.size() - notSaved.size();
    return new FulcrumRefreshResult(unchanged.size() + updated, updated, skipped);
  }

  private static List<SkippedSensor> notFoundAmong(Set<UUID> sensorIds, List<Sensor> found) {
    Set<UUID> foundIds = Set.copyOf(idsOf(found));
    return sensorIds.stream()
        .filter(id -> !foundIds.contains(id))
        .map(id -> new SkippedSensor(id, Reason.NOT_FOUND))
        .toList();
  }

  private static Comparison compare(Sensor sensor, Map<UUID, FulcrumSensor> fulcrumSensorsById) {
    if (sensor.getFulcrumId() == null) {
      return new Comparison.Skipped(Reason.NO_FULCRUM_ID);
    }
    FulcrumSensor fulcrumSensor = fulcrumSensorsById.get(sensor.getFulcrumId());
    if (fulcrumSensor == null) {
      return new Comparison.Skipped(Reason.NOT_IN_FULCRUM);
    }
    return coordinatesOf(fulcrumSensor)
        .<Comparison>map(
            coordinates ->
                coordinates.equals(sensor.getCoordinates())
                    ? new Comparison.Unchanged()
                    : new Comparison.Changed(coordinates))
        .orElseGet(() -> new Comparison.Skipped(Reason.COORDINATES_MISSING));
  }

  private List<SkippedSensor> saveChanged(List<Sensor> changed, OffsetDateTime refreshedAt) {
    List<SkippedSensor> notSaved = new ArrayList<>();
    for (Sensor sensor : changed) {
      sensor.setLastFulcrumRefreshAt(refreshedAt);
      try {
        sensorService.saveRefreshedCoordinates(sensor);
      } catch (DataChangedException _) {
        // saved by someone else since it was read, one conflict must not block the others
        notSaved.add(new SkippedSensor(sensor.getId(), Reason.CHANGED_MEANWHILE));
      }
    }
    return notSaved;
  }

  private static List<UUID> idsOf(Collection<Sensor> sensors) {
    return sensors.stream().map(Sensor::getId).toList();
  }

  private sealed interface Comparison {
    record Skipped(Reason reason) implements Comparison {}

    record Unchanged() implements Comparison {}

    record Changed(Coordinates coordinates) implements Comparison {}
  }
}
