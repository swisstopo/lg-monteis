package ch.swisstopo.monteis.core.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.DAS;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class SensorConfigPublisherTest {

  @Mock private KafkaTemplate<String, SensorParameterConfig> kafkaTemplate;

  @Captor private ArgumentCaptor<SensorParameterConfig> configCaptor;

  private static Sensor sensor() {
    return new Sensor(
        "Test Sensor",
        "BH12-P03",
        DAS.SOL_EXPERTS,
        null,
        null,
        new Coordinates(0, 0, 0),
        true,
        null);
  }

  private static SensorParameter parameter(UUID id, boolean active, String dasParameterAlias) {
    Formula formula = new Formula();
    formula.setExpression("x * 2");
    return new SensorParameter(
        id,
        "Temperature",
        dasParameterAlias,
        new SensorType(null, "Other", null),
        Unit.METER,
        formula,
        new AlarmLimits(0.0, 100.0),
        active,
        null,
        3);
  }

  @Test
  void should_publish_sensor_parameter_config_keyed_by_composite_das_key() {
    // given
    String topic = "internal-sensor-config";
    SensorConfigPublisher publisher = new SensorConfigPublisher(kafkaTemplate, topic);

    Sensor sensor = sensor();
    UUID parameterId = UUID.randomUUID();
    SensorParameter parameter = parameter(parameterId, true, "temperature");

    // when
    publisher.publish(sensor, parameter);

    // then
    then(kafkaTemplate)
        .should()
        .send(eq(topic), eq("SOL_EXPERTS__BH12-P03__temperature"), configCaptor.capture());
    SensorParameterConfig sentConfig = configCaptor.getValue();
    assertThat(sentConfig.getDas()).isEqualTo(Das.SOL_EXPERTS);
    assertThat(sentConfig.getDasSensorAlias()).isEqualTo("BH12-P03");
    assertThat(sentConfig.getDasParameterAlias()).isEqualTo("temperature");
    assertThat(sentConfig.getSensorParameterId()).isEqualTo(parameterId);
    assertThat(sentConfig.getFormula()).isEqualTo("x * 2");
    assertThat(sentConfig.getUpperBound()).isEqualTo(100.0);
    assertThat(sentConfig.getLowerBound()).isEqualTo(0.0);
    assertThat(sentConfig.getVersion()).isEqualTo(3);
  }

  @Test
  void should_not_publish_inactive_parameter() {
    // given
    SensorConfigPublisher publisher =
        new SensorConfigPublisher(kafkaTemplate, "internal-sensor-config");
    SensorParameter parameter = parameter(UUID.randomUUID(), false, "temperature");

    // when
    publisher.publish(sensor(), parameter);

    // then
    then(kafkaTemplate).should(never()).send(any(), any(), any());
  }

  @Test
  void should_not_publish_parameter_with_blank_das_parameter_alias() {
    // given
    SensorConfigPublisher publisher =
        new SensorConfigPublisher(kafkaTemplate, "internal-sensor-config");
    SensorParameter parameter = parameter(UUID.randomUUID(), true, " ");

    // when
    publisher.publish(sensor(), parameter);

    // then
    then(kafkaTemplate).should(never()).send(any(), any(), any());
  }
}
