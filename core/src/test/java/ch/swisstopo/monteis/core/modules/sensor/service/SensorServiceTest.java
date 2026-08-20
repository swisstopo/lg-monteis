package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {
  @Mock private SensorRepository repository;
  @Mock private SensorConfigPublisher configPublisher;

  @InjectMocks private SensorService service;

  @Test
  void should_delegate_create_sensor_to_repository() {
    // given
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);

    given(repository.create(inputSensor)).willReturn(expectedSensor);

    // when
    Sensor actualSensor = service.createSensor(inputSensor);

    // then
    then(repository).should().create(inputSensor);
    then(configPublisher).should().publish(expectedSensor);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_delegate_update_sensor_to_repository() {
    // given
    Sensor inputSensor = mock(Sensor.class);
    Sensor expectedSensor = mock(Sensor.class);

    given(repository.update(inputSensor)).willReturn(expectedSensor);

    // when
    Sensor actualSensor = service.updateSensor(inputSensor);

    // then
    then(repository).should().update(inputSensor);
    assertEquals(expectedSensor, actualSensor);
  }

  @Test
  void should_publish_config_when_formula_changed() {
    // given
    Sensor before = sensorWith(1L, "x * 2", 0.0, 100.0);
    Sensor after = sensorWith(1L, "x * 3", 0.0, 100.0);

    given(repository.findById(1L)).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should().publish(after);
  }

  @Test
  void should_publish_config_when_alarm_limits_changed() {
    // given
    Sensor before = sensorWith(1L, "x * 2", 0.0, 100.0);
    Sensor after = sensorWith(1L, "x * 2", 0.0, 200.0);

    given(repository.findById(1L)).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should().publish(after);
  }

  @Test
  void should_not_publish_config_when_only_unrelated_fields_changed() {
    // given
    Sensor before = sensorWith(1L, "x * 2", 0.0, 100.0);
    before.setName("Old Name");
    Sensor after = sensorWith(1L, "x * 2", 0.0, 100.0);
    after.setName("New Name");

    given(repository.findById(1L)).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should(never()).publish(any());
  }

  private Sensor sensorWith(Long id, String formulaExpression, Double lower, Double upper) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    Sensor sensor =
        new Sensor(
            "CODE-" + id,
            "Name",
            new SensorType(null, "Other", null),
            Unit.METER,
            null,
            new Coordinates(0, 0, 0),
            new AlarmLimits(lower, upper),
            true,
            formula);
    sensor.setId(id);
    return sensor;
  }
}
