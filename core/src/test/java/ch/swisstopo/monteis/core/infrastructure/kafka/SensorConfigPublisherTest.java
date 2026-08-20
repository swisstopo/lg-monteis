package ch.swisstopo.monteis.core.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class SensorConfigPublisherTest {

  @Mock private KafkaTemplate<String, SensorConfig> kafkaTemplate;

  @Captor private ArgumentCaptor<SensorConfig> configCaptor;

  @Test
  void should_publish_sensor_config_keyed_by_code() {
    // given
    String topic = "internal-sensor-config";
    SensorConfigPublisher publisher = new SensorConfigPublisher(kafkaTemplate, topic);

    Formula formula = new Formula();
    formula.setExpression("x * 2");
    Sensor sensor =
        new Sensor(
            "SENS-001",
            "Test Sensor",
            new SensorType(null, "Other", null),
            Unit.METER,
            null,
            new Coordinates(0, 0, 0),
            new AlarmLimits(0.0, 100.0),
            true,
            formula);
    sensor.setVersion(3);

    // when
    publisher.publish(sensor);

    // then
    then(kafkaTemplate).should().send(eq(topic), eq("SENS-001"), configCaptor.capture());
    SensorConfig sentConfig = configCaptor.getValue();
    assertThat(sentConfig.getSensorId()).isEqualTo("SENS-001");
    assertThat(sentConfig.getFormula()).isEqualTo("x * 2");
    assertThat(sentConfig.getUpperBound()).isEqualTo(100.0);
    assertThat(sentConfig.getLowerBound()).isEqualTo(0.0);
    assertThat(sentConfig.getVersion()).isEqualTo(3);
  }
}
