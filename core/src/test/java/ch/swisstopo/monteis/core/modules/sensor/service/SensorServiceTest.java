package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.List;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {
  @Mock private SensorRepository repository;

  @Mock private Javers javers;

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
  void should_return_all_formulas_from_repository() {
    // given
    Formula f1 = mock(Formula.class);
    Formula f2 = mock(Formula.class);

    List<Formula> expectedFormulas = List.of(f1, f2);

    given(repository.findAllFormulas()).willReturn(expectedFormulas);

    // when
    List<Formula> actualFormulas = service.findAllFormulas();

    // then
    then(repository).should().findAllFormulas();
    assertEquals(expectedFormulas, actualFormulas);
  }

  @Test
  void should_stream_unaudited_sensors_and_commit_to_javers() {
    // given
    Sensor sensor1 = mock(Sensor.class);
    Sensor sensor2 = mock(Sensor.class);

    // We use a real stream backed by our mocked objects so the try-with-resources executes normally
    Stream<Sensor> mockStream = Stream.of(sensor1, sensor2);

    given(repository.streamUnauditedSensors()).willReturn(mockStream);

    // when
    service.backfillMissingSnapshots();

    // then
    then(repository).should().streamUnauditedSensors();
    then(javers).should().commit("SYSTEM_SEEDER", sensor1);
    then(javers).should().commit("SYSTEM_SEEDER", sensor2);
  }
}
