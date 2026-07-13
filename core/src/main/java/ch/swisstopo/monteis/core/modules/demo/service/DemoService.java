package ch.swisstopo.monteis.core.modules.demo.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.modules.demo.jooq.DemoRepository;
import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

  //  @AuditChanges
  //  public WriteSensorDto saveOrUpdateSensor(WriteSensorDto dto) {
  //    return repository.saveSensorWithFormula(dto);
  //  }

  public ReadSimpleMetric fetchErrors(boolean testError) {
    List<ReadSimpleMetric> results = repository.fetchRecentMetrics(1);
    ReadSimpleMetric finalResult = results.getFirst();
    if (testError) {
      throw new ObjectBusinessValidationException("sensor.age.too.not.valid", Map.of("min", 18));
    }
    return finalResult;
  }

  public ReadSimpleMetric fetchException(boolean testRuntimeException) {
    List<ReadSimpleMetric> results = repository.fetchRecentMetrics(1);
    ReadSimpleMetric finalResult = results.getFirst();
    if (testRuntimeException) {
      throw new NullPointerException("Something went horribly wrong, nullpointer");
    }
    return finalResult;
  }
}
