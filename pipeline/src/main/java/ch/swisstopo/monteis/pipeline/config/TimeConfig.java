package ch.swisstopo.monteis.pipeline.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    // systemUTC() is highly recommended for backend data pipelines
    // to prevent timezone shifting issues.
    return Clock.systemUTC();
  }
}
