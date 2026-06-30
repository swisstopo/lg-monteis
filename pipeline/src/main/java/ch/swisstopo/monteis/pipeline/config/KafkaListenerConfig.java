package ch.swisstopo.monteis.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaListenerConfig {

  @Bean("singleMessageFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> singleMessageFactory(
      ConsumerFactory<String, Object> consumerFactory) {

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);

    factory.setBatchListener(false);

    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    return factory;
  }
}
