package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSensor;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

  private static final UUID FULCRUM_ID = UUID.fromString("29d8aee7-d9c6-4459-ac9d-bbd6e10f6518");
  private static final Coordinates STORED_COORDINATES = new Coordinates(1.0, 2.0, 3.0);

  @Mock private SensorRepository repository;
  @Mock private SensorConfigPublisher configPublisher;
  @Mock private FulcrumService fulcrumService;
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

  @InjectMocks private SensorService service;

  private static SensorParameter parameter(UUID id, String formulaExpression) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    return new SensorParameter(
        id,
        "Temperature",
        "SENS-001",
        new SensorType(null, "Other", null),
        Unit.METER,
        formula,
        new AlarmLimits(0.0, 100.0),
        true,
        null,
        1);
  }

  @Test
  void should_delegate_create_sensor_to_repository() {
    // given
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);
    SensorParameter createdParameter = parameter(UUID.randomUUID(), "x * 2");
    givenTheSensorExistsInFulcrum(inputSensor);

    given(repository.create(inputSensor)).willReturn(expectedSensor);
    given(expectedSensor.getParameters()).willReturn(List.of(createdParameter));

    // when
    Sensor actualSensor = service.createSensor(inputSensor);

    // then
    then(repository).should().create(inputSensor);
    then(configPublisher).should().publish(expectedSensor, createdParameter);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_delegate_update_sensor_to_repository() {
    // given
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);
    givenTheSensorExistsInFulcrum(inputSensor);

    given(repository.update(inputSensor)).willReturn(expectedSensor);

    // when
    Sensor actualSensor = service.updateSensor(inputSensor);

    // then
    then(repository).should().update(inputSensor);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_publish_config_when_change_triggers_publish_returns_true() {
    // given
    UUID parameterId = UUID.randomUUID();
    SensorParameter parameterBefore = parameter(parameterId, "x");
    SensorParameter parameterAfter = parameter(parameterId, "x * 2");

    Sensor before = mock(Sensor.class);
    Sensor after = mock(Sensor.class);
    givenTheSensorExistsInFulcrum(after);
    given(before.getParameters()).willReturn(List.of(parameterBefore));
    given(after.getParameters()).willReturn(List.of(parameterAfter));

    given(repository.findById(any())).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should().publish(after, parameterAfter);
  }

  @Test
  void should_not_publish_config_when_change_triggers_publish_returns_false() {
    // given
    UUID parameterId = UUID.randomUUID();
    SensorParameter parameterBefore = parameter(parameterId, "x");
    SensorParameter parameterAfter = parameter(parameterId, "x");

    Sensor before = mock(Sensor.class);
    Sensor after = mock(Sensor.class);
    givenTheSensorExistsInFulcrum(after);
    given(before.getParameters()).willReturn(List.of(parameterBefore));
    given(after.getParameters()).willReturn(List.of(parameterAfter));

    given(repository.findById(any())).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should(never()).publish(any(), any());
  }

  @Test
  void should_reject_create_when_the_sensor_is_unknown_to_fulcrum() {
    // given
    Sensor inputSensor = mock(Sensor.class);

    given(inputSensor.hasFulcrumId()).willReturn(true);
    given(inputSensor.getFulcrumId()).willReturn(FULCRUM_ID);
    given(fulcrumService.findSensorByFulcrumId(FULCRUM_ID)).willReturn(Optional.empty());

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class, () -> service.createSensor(inputSensor));

    // then
    assertEquals("fulcrum.sensor.not-found", exception.getMessageKey());
    then(repository).should(never()).create(any());
  }

  @Test
  void should_create_without_asking_fulcrum_when_the_sensor_has_no_fulcrum_id() {
    // given: the Fulcrum ID is optional on a sensor, and a sensor without one has no record to
    // read coordinates from - the ones it was given have to survive
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);

    given(repository.create(inputSensor)).willReturn(expectedSensor);

    // when
    Sensor actualSensor = service.createSensor(inputSensor);

    // then
    then(fulcrumService).should(never()).findSensorByFulcrumId(any());
    then(inputSensor).should(never()).setCoordinates(any());
    then(repository).should().create(inputSensor);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_update_without_asking_fulcrum_when_the_sensor_has_no_fulcrum_id() {
    // given
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);

    given(repository.update(inputSensor)).willReturn(expectedSensor);

    // when
    Sensor actualSensor = service.updateSensor(inputSensor);

    // then
    then(fulcrumService).should(never()).findSensorByFulcrumId(any());
    then(inputSensor).should(never()).setCoordinates(any());
    then(repository).should().update(inputSensor);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_reject_update_when_the_sensor_is_unknown_to_fulcrum() {
    // given
    Sensor inputSensor = mock(Sensor.class);

    given(inputSensor.hasFulcrumId()).willReturn(true);
    given(inputSensor.getFulcrumId()).willReturn(FULCRUM_ID);
    given(fulcrumService.findSensorByFulcrumId(FULCRUM_ID)).willReturn(Optional.empty());

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class, () -> service.updateSensor(inputSensor));

    // then
    assertEquals("fulcrum.sensor.not-found", exception.getMessageKey());
    then(repository).should(never()).update(any());
  }

  @Test
  void should_reject_create_when_the_fulcrum_record_has_no_coordinates() {
    // given: the record exists, but Fulcrum never computed a height for it
    Sensor inputSensor = mock(Sensor.class);

    givenTheSensorExistsInFulcrum(inputSensor, 2579000.0, 1247000.0, null);

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class, () -> service.createSensor(inputSensor));

    // then
    assertEquals("sensor.fulcrum.coordinatesMissing", exception.getMessageKey());
    assertEquals("z", exception.getParams().get("missing"));
    assertEquals(FULCRUM_ID.toString(), exception.getParams().get("fulcrumId"));
    then(inputSensor).should(never()).setCoordinates(any());
    then(repository).should(never()).create(any());
  }

  @Test
  void should_reject_update_when_the_fulcrum_record_has_no_coordinates() {
    // given
    Sensor inputSensor = mock(Sensor.class);

    givenTheSensorExistsInFulcrum(inputSensor, null, null, 500.0);

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class, () -> service.updateSensor(inputSensor));

    // then
    assertEquals("sensor.fulcrum.coordinatesMissing", exception.getMessageKey());
    assertEquals("x, y", exception.getParams().get("missing"));
    then(inputSensor).should(never()).setCoordinates(any());
    then(repository).should(never()).update(any());
  }

  @Test
  void should_set_the_last_fulcrum_refresh_when_a_sensor_with_a_fulcrum_id_is_created() {
    // given
    Sensor sensor = sensor(FULCRUM_ID, null);
    givenFulcrumHolds(2579000.0, 1247000.0, 500.0);
    given(repository.create(sensor)).willReturn(sensor);

    // when
    service.createSensor(sensor);

    // then
    assertEquals(OffsetDateTime.now(clock), sensor.getLastFulcrumRefreshAt());
    assertEquals(new Coordinates(2579000.0, 1247000.0, 500.0), sensor.getCoordinates());
  }

  @Test
  void should_set_the_last_fulcrum_refresh_when_a_sensor_with_a_fulcrum_id_is_updated() {
    // given
    Sensor sensor = sensor(FULCRUM_ID, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    givenFulcrumHolds(2579000.0, 1247000.0, 500.0);
    given(repository.update(sensor)).willReturn(sensor);

    // when
    service.updateSensor(sensor);

    // then
    assertEquals(OffsetDateTime.now(clock), sensor.getLastFulcrumRefreshAt());
  }

  @Test
  void should_clear_the_last_fulcrum_refresh_when_the_fulcrum_id_is_removed() {
    // given
    Sensor sensor = sensor(null, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    given(repository.update(sensor)).willReturn(sensor);

    // when
    service.updateSensor(sensor);

    // then
    assertNull(sensor.getLastFulcrumRefreshAt());
    assertEquals(STORED_COORDINATES, sensor.getCoordinates());
  }

  @Test
  void should_return_sensor_when_found_by_id() {
    // given
    UUID id = UUID.randomUUID();
    Sensor expectedSensor = mock(Sensor.class);

    given(repository.findById(id)).willReturn(Optional.of(expectedSensor));

    // when
    Sensor actualSensor = service.getSensor(id);

    // then
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_throw_when_sensor_not_found_by_id() {
    // given
    UUID id = UUID.randomUUID();

    given(repository.findById(id)).willReturn(Optional.empty());

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getSensor(id));
  }

  @Test
  void should_delegate_find_all_formulas_to_repository() {
    // given
    List<Formula> expectedFormulas = List.of(mock(Formula.class));

    given(repository.findAllFormulas()).willReturn(expectedFormulas);

    // when
    List<Formula> actualFormulas = service.findAllFormulas();

    // then
    assertEquals(expectedFormulas, actualFormulas);
  }

  @Test
  void should_delegate_find_all_types_to_repository() {
    // given
    List<SensorType> expectedTypes = List.of(new SensorType(UUID.randomUUID(), "Other", 1));

    given(repository.findAllTypes()).willReturn(expectedTypes);

    // when
    List<SensorType> actualTypes = service.findAllTypes();

    // then
    assertEquals(expectedTypes, actualTypes);
  }

  /**
   * Both write paths look the sensor up in Fulcrum when it carries a Fulcrum ID, and refuse to
   * continue when the record is not there. A sensor without an ID skips the lookup entirely, so a
   * test that wants the lookup to happen has to give the sensor one.
   */
  private void givenTheSensorExistsInFulcrum(Sensor sensor) {
    givenTheSensorExistsInFulcrum(sensor, 2579000.0, 1247000.0, 500.0);
  }

  /**
   * Every column of a Fulcrum record is nullable, coordinates included, so a test that wants the
   * write to go through has to hand the record a complete position.
   */
  private void givenTheSensorExistsInFulcrum(Sensor sensor, Double x, Double y, Double z) {
    given(sensor.hasFulcrumId()).willReturn(true);
    given(sensor.getFulcrumId()).willReturn(FULCRUM_ID);
    givenFulcrumHolds(x, y, z);
  }

  private void givenFulcrumHolds(Double x, Double y, Double z) {
    FulcrumSensor fulcrumSensor =
        new FulcrumSensor(FULCRUM_ID, "BSW04_PP_1", List.of("Sensor"), x, y, z);
    given(fulcrumService.findSensorByFulcrumId(FULCRUM_ID)).willReturn(Optional.of(fulcrumSensor));
  }

  private static Sensor sensor(UUID fulcrumId, OffsetDateTime lastFulcrumRefreshAt) {
    Sensor sensor =
        new Sensor(
            UUID.randomUUID(),
            "BSW04_PP_1",
            null,
            Das.SOL_EXPERTS,
            fulcrumId,
            null,
            STORED_COORDINATES,
            true,
            null,
            1,
            List.of());
    sensor.setLastFulcrumRefreshAt(lastFulcrumRefreshAt);
    return sensor;
  }
}
