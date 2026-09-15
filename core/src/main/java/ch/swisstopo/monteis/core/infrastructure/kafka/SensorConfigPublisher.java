package ch.swisstopo.monteis.core.infrastructure.kafka;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigPublisher {

  private final KafkaTemplate<String, SensorParameterConfig> kafkaTemplate;
  private final String sensorConfigTopic;

  public SensorConfigPublisher(
      KafkaTemplate<String, SensorParameterConfig> kafkaTemplate,
      @Value("${app.kafka.topics.sensor-config}") String sensorConfigTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.sensorConfigTopic = sensorConfigTopic;
  }

  /**
   * Publishes the config for a single sensor parameter, keyed by the composite DAS ingest key
   * ({@code <DAS>__<das_sensor_alias>__<das_parameter_alias>}). Published regardless of {@code
   * active} - that flag only filters what the UI fetches, the pipeline still needs to normalize
   * and backfill readings for inactive parameters so their historical/ongoing data stays correct
   * and available whenever an inactive sensor is looked at. Skips only parameters with a blank
   * {@code dasParameterAlias}, since that can't be resolved to a valid Kafka key.
   */
  public void publish(Sensor sensor, SensorParameter parameter) {
    String dasParameterAlias = parameter.getDasParameterAlias();
    if (dasParameterAlias == null || dasParameterAlias.isBlank()) {
      return;
    }

    Das das = sensor.getDAS();
    String dasKey = DasKey.compose(das, sensor.getDasSensorAlias(), dasParameterAlias);

    SensorParameterConfig config =
        new SensorParameterConfig()
            .das(das)
            .dasSensorAlias(sensor.getDasSensorAlias())
            .dasParameterAlias(dasParameterAlias)
            .sensorParameterId(parameter.getId())
            .formula(parameter.getFormula().getExpression())
            .upperBound(parameter.getAlarmLimits().upper())
            .lowerBound(parameter.getAlarmLimits().lower())
            .version(parameter.getVersion());

    kafkaTemplate.send(sensorConfigTopic, dasKey, config);
  }
}
