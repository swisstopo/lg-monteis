package ch.swisstopo.monteis.core.infrastructure.kafka;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigPublisher {

  private final KafkaTemplate<String, SensorConfig> kafkaTemplate;
  private final String sensorConfigTopic;

  public SensorConfigPublisher(
      KafkaTemplate<String, SensorConfig> kafkaTemplate,
      @Value("${app.kafka.topics.sensor-config}") String sensorConfigTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.sensorConfigTopic = sensorConfigTopic;
  }

  public void publish(Sensor sensor) {
    SensorConfig config =
        new SensorConfig()
            .sensorId(sensor.getCode())
            .formula(sensor.getFormula().getExpression())
            .upperBound(sensor.getAlarmLimits().upper())
            .lowerBound(sensor.getAlarmLimits().lower())
            .version(sensor.getVersion());
    kafkaTemplate.send(sensorConfigTopic, sensor.getCode(), config);
  }
}
