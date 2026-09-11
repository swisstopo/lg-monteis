package ch.swisstopo.monteis.core.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.DAS;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.util.List;
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
  void should_publish_sensor_config_keyed_by_das_parameter_alias() {
    // given
    String topic = "internal-sensor-config";
    SensorConfigPublisher publisher = new SensorConfigPublisher(kafkaTemplate, topic);

    Formula formula = new Formula();
    formula.setExpression("x * 2");
    SensorParameter parameter =
        new SensorParameter(
            null,
            "Temperature",
            "SENS-001",
            new SensorType(null, "Other", null),
            Unit.METER,
            formula,
            new AlarmLimits(0.0, 100.0),
            true,
            null,
            null);
    Sensor sensor =
        new Sensor(
            "Test Sensor", null, DAS.SOL_EXPERTS, null, null, new Coordinates(0, 0, 0), true, null);
    sensor.setParameters(List.of(parameter));
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
