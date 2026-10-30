package ch.swisstopo.monteis.core.infrastructure.scheduling;

import java.util.concurrent.Executors;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {

  // db time: drifting pod clocks agree on expiry. keep-alive: ShedLock unlocks by name only, an
  // expired holder would release the next holder's lock. executor no bean: would disable Boot's
  // task executor/scheduler, sync would run on the thread extending its lock
  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new KeepAliveLockProvider(
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()),
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("lock-keep-alive").daemon().factory()));
  }
}
