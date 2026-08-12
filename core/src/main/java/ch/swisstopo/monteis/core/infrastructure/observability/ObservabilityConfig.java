package ch.swisstopo.monteis.core.infrastructure.observability;

import org.jooq.ExecuteListenerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Read-path timing, switched on per environment with {@code
 * monteis.observability.request-timing.enabled=true}.
 *
 * <p>Off by default, and when off nothing is registered — no filter, no jOOQ listener — so there is
 * no cost to leaving the code in place. Safe to enable in production: it adds exactly one log line
 * per request and logs no SQL, so no query text or bind parameter can reach the log.
 *
 * <p>For the statements themselves use jOOQ's own listener ({@code
 * logging.level.org.jooq.tools.LoggerListener=DEBUG}) rather than duplicating it here.
 */
@Configuration
@ConditionalOnProperty(name = "monteis.observability.request-timing.enabled", havingValue = "true")
public class ObservabilityConfig {

  /** Ordered first so the measured time covers the whole filter chain and MVC stack. */
  @Bean
  public FilterRegistrationBean<RequestTimingFilter> requestTimingFilter() {
    var registration = new FilterRegistrationBean<>(new RequestTimingFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * Spring Boot's jOOQ auto-configuration collects every {@link ExecuteListenerProvider} bean and
   * adds it to the DSL configuration, so declaring this bean is enough — no need to touch the
   * existing providers.
   *
   * <p>A method reference, not a fixed instance: {@link QueryTimingListener} keeps per-statement
   * state, so each execution needs its own.
   */
  @Bean
  public ExecuteListenerProvider queryTimingListenerProvider() {
    return QueryTimingListener::new;
  }
}
