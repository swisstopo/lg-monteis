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

  public static final String FULCRUM_DOCUMENT_SYNC_LOCK = "fulcrum-document-sync";

  // db time, so pods with drifting clocks still agree on when a lock expired. kept alive while its
  // holder runs, ShedLock's unlock matches the lock name only, so a lock that expired under a
  // running holder would be released by it once a second holder took it over. the keep-alive
  // executor is no bean: as one it would turn off Boot's task executor and scheduler, and the
  // @Scheduled sync would run on the very thread that has to extend its lock
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
