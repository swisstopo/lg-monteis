package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.kafka.SensorConfigPublisher;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    // when
    ObjectBusinessValidationException exception =
        assertThrows(ObjectBusinessValidationException.class, () -> service.getSensor(id));

    // then
    assertEquals("object.deleted", exception.getMessageKey());
  }

  @Test
  void should_delegate_get_sensors_to_repository() {
    // given
    PagedRequest request = new PagedRequest(0, 20, List.of(), Map.of());
    PagedResult<Sensor> expectedResult = new PagedResult<>(List.of(mock(Sensor.class)), 1);

    given(repository.findPaged(request)).willReturn(expectedResult);

    // when
    PagedResult<Sensor> actualResult = service.getSensors(request);

    // then
    assertEquals(expectedResult, actualResult);
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
}
