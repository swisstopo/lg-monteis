package ch.swisstopo.monteis.core.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

class SchedulingConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
          .withUserConfiguration(SchedulingConfig.class)
          .withBean(DataSource.class, () -> mock(DataSource.class));

  @Test
  void should_keep_boots_task_executor_for_the_background_sync() {
    // when / then
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("applicationTaskExecutor");
          assertThat(context.getBean("applicationTaskExecutor")).isInstanceOf(TaskExecutor.class);
        });
  }

  @Test
  void should_run_scheduled_jobs_on_boots_scheduler_not_on_the_lock_keep_alive_thread() {
    // when / then
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TaskScheduler.class);
          assertThat(context).doesNotHaveBean(ScheduledExecutorService.class);
          assertThat(context).hasSingleBean(LockProvider.class);
        });
  }
}
