package ch.swisstopo.monteis.core.modules.demo.service;

import ch.swisstopo.monteis.core.infrastructure.AuditChanges;
import ch.swisstopo.monteis.core.modules.demo.jooq.DemoRepository;
import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {

  private static final Logger log = LoggerFactory.getLogger(DemoService.class);

  private final DemoRepository repository;

  public DemoService(DemoRepository repository) {
    this.repository = repository;
  }

  public List<ReadSimpleMetric> fetchRecentMetrics(int limit) {
    List<ReadSimpleMetric> results = repository.fetchRecentMetrics(limit);

    if (!results.isEmpty()) {
      for (ReadSimpleMetric result : results) {
        log.debug(result.normValue().toString());
      }
    } else {
      log.error("fetching worked but nothing found");
    }
    return results;
  }

  @Transactional
  @AuditChanges
  public WriteSensorDto saveOrUpdateSensor(WriteSensorDto dto) {
    return repository.saveSensorWithFormula(dto);
  }
}
