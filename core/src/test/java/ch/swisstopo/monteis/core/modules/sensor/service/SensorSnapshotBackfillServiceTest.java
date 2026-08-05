package ch.swisstopo.monteis.core.modules.sensor.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorSnapshotBackfillServiceTest {
  @Mock private SensorRepository repository;

  @Mock private Javers javers;

  @InjectMocks private SensorSnapshotBackfillService service;

  @Test
  void should_stream_unaudited_sensors_and_commit_to_javers() {
    // given
    Sensor sensor1 = mock(Sensor.class);
    Sensor sensor2 = mock(Sensor.class);

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
