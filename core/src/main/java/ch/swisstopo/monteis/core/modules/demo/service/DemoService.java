package ch.swisstopo.monteis.core.modules.demo.service;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.RawSimpleMetricsRecord;
import ch.swisstopo.monteis.core.modules.demo.jooq.DemoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class DemoService {

  private static final Logger log = LoggerFactory.getLogger(DemoService.class);

  private final DemoRepository repository;

  public DemoService(DemoRepository repository) {
    this.repository = repository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logMetricsOnStartup() {
    List<RawSimpleMetricsRecord> results = repository.fetchRecentMetrics(5);

    if (!results.isEmpty()) {
      for (RawSimpleMetricsRecord result : results) {
        log.debug(result.toString());
        log.debug(result.getValue().toString());
      }
    } else {
      log.error("fetching worked but nothing found");
    }
  }
}
