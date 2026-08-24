package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
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
  void should_publish_config_when_change_triggers_publish_returns_true() {
    // given
    Sensor before = mock(Sensor.class);
    Sensor after = mock(Sensor.class);

    given(repository.findById(any())).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);
    given(after.changeTriggersPublish(before)).willReturn(true);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should().publish(after);
  }

  @Test
  void should_not_publish_config_when_change_triggers_publish_returns_false() {
    // given
    Sensor before = mock(Sensor.class);
    Sensor after = mock(Sensor.class);

    given(repository.findById(any())).willReturn(Optional.of(before));
    given(repository.update(after)).willReturn(after);
    given(after.changeTriggersPublish(before)).willReturn(false);

    // when
    service.updateSensor(after);

    // then
    then(configPublisher).should(never()).publish(any());
  }
}
