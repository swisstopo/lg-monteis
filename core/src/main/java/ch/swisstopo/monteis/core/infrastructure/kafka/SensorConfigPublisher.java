package ch.swisstopo.monteis.core.infrastructure.kafka;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
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
    for (SensorParameter parameter : sensor.getParameters()) {

      if (Boolean.FALSE.equals(parameter.getActive())) {
        continue;
      }

      String identifier = parameter.getDasParameterAlias();

      if (identifier == null || identifier.isBlank()) {
        continue;
      }

      SensorConfig config =
          new SensorConfig()
              .sensorId(identifier)
              .formula(parameter.getFormula().getExpression())
              .upperBound(parameter.getAlarmLimits().upper())
              .lowerBound(parameter.getAlarmLimits().lower())
              .version(sensor.getVersion());

      kafkaTemplate.send(sensorConfigTopic, identifier, config);
    }
  }
}
