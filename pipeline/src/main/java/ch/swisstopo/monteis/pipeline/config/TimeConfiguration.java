package ch.swisstopo.monteis.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    @Bean
    public Clock clock() {
        // systemUTC() is highly recommended for backend data pipelines
        // to prevent timezone shifting issues.
        return Clock.systemUTC();
    }
}