package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

  // unknown, hidden or without fulcrum id dropped silently: tells nothing about hidden sensors
  public void refreshSelected(Set<UUID> sensorIds) {
    refresh(repository.findAllWithFulcrumIdAmong(sensorIds));
  }

  public void refreshAllVisible() {
    refresh(repository.findAllWithFulcrumId());
  }

  // no transaction around it: each save commits, a failed refresh keeps what it saved
  private void refresh(List<Sensor> sensors) {
    // read before any write: Fulcrum failing leaves every sensor as it was
    Map<UUID, FulcrumSensor> fulcrumSensorsById =
        fulcrumService.findSensorsByFulcrumId(fulcrumIdsOf(sensors));
    OffsetDateTime refreshedAt = OffsetDateTime.now(clock);
    List<FulcrumReading> readings = readingsOf(sensors, fulcrumSensorsById);
    List<FulcrumReading> unchanged =
        readings.stream().filter(reading -> !reading.changesCoordinates()).toList();
    List<FulcrumReading> changed =
        readings.stream().filter(FulcrumReading::changesCoordinates).toList();

    repository.markFulcrumRefreshed(idsOf(unchanged), refreshedAt);
    changed.forEach(reading -> saveCoordinates(reading, refreshedAt));
  }

  private static Set<UUID> fulcrumIdsOf(List<Sensor> sensors) {
    return sensors.stream().map(Sensor::getFulcrumId).collect(Collectors.toSet());
  }

  // record missing or incomplete in Fulcrum => no reading, stale last refresh shows it
  private static List<FulcrumReading> readingsOf(
      List<Sensor> sensors, Map<UUID, FulcrumSensor> fulcrumSensorsById) {
    return sensors.stream()
        .flatMap(
            sensor ->
                Optional.ofNullable(fulcrumSensorsById.get(sensor.getFulcrumId()))
                    .flatMap(FulcrumCoordinatesMapper::coordinatesOf)
                    .map(coordinates -> new FulcrumReading(sensor, coordinates))
                    .stream())
        .toList();
  }

  private static List<UUID> idsOf(List<FulcrumReading> readings) {
    return readings.stream().map(reading -> reading.sensor().getId()).toList();
  }

  // optimistic lock: a sensor saved meanwhile fails the refresh, the other save is not overwritten
  private void saveCoordinates(FulcrumReading reading, OffsetDateTime refreshedAt) {
    Sensor sensor = reading.sensor();
    sensor.setCoordinates(reading.coordinates());
    sensor.setLastFulcrumRefreshAt(refreshedAt);
    sensorService.saveRefreshedCoordinates(sensor);
  }

  private record FulcrumReading(Sensor sensor, Coordinates coordinates) {

    boolean changesCoordinates() {
      return !coordinates.equals(sensor.getCoordinates());
    }
  }
}
