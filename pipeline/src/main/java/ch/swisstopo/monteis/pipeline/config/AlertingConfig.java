package ch.swisstopo.monteis.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync(proxyTargetClass = true)
public class AlertingConfig {

  private static final Logger log = LoggerFactory.getLogger(AlertingConfig.class);

  public static final String ALERT_EXECUTOR = "alertPublisherExecutor";

  private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

  @Bean(ALERT_EXECUTOR)
  @DependsOn("alertPublisher")
  public ThreadPoolTaskExecutor alertPublisherExecutor(
      @Value("${app.pipeline.alerts.queue-capacity:1000}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("alert-publisher-");
    executor.setRejectedExecutionHandler(
        (task, pool) ->
            log.warn("Alert queue full ({} queued), dropping alert", pool.getQueue().size()));
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
    return executor;
  }
}
