package ch.swisstopo.monteis.core.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.ExecuteListenerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Pins the per-environment switch: nothing is registered unless {@code
 * monteis.observability.request-timing.enabled=true}, so leaving the feature in place costs a
 * disabled environment nothing.
 */
class ObservabilityConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of())
          .withUserConfiguration(ObservabilityConfig.class);

  @Test
  void should_register_nothing_by_default() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ObservabilityConfig.class);
          assertThat(context).doesNotHaveBean(ExecuteListenerProvider.class);
        });
  }

  @Test
  void should_register_nothing_when_explicitly_disabled() {
    contextRunner
        .withPropertyValues("monteis.observability.request-timing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ObservabilityConfig.class));
  }

  @Test
  void should_register_the_filter_and_the_jooq_listener_when_enabled() {
    contextRunner
        .withPropertyValues("monteis.observability.request-timing.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObservabilityConfig.class);
              assertThat(context).hasSingleBean(ExecuteListenerProvider.class);
              assertThat(
                      context.getBean(ObservabilityConfig.class).requestTimingFilter().getFilter())
                  .isInstanceOf(RequestTimingFilter.class);
            });
  }

  @Test
  void should_provide_a_fresh_listener_per_execution() {
    contextRunner
        .withPropertyValues("monteis.observability.request-timing.enabled=true")
        .run(
            context -> {
              ExecuteListenerProvider provider = context.getBean(ExecuteListenerProvider.class);

              // The listener holds per-statement state, so a shared instance would corrupt
              // concurrent timings
              assertThat(provider.provide()).isNotSameAs(provider.provide());
            });
  }
}
