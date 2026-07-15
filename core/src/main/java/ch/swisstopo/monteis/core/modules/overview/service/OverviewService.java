package ch.swisstopo.monteis.core.modules.overview.service;

import ch.swisstopo.monteis.core.modules.overview.jooq.OverviewRepository;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OverviewService {

  private static final Logger log = LoggerFactory.getLogger(OverviewService.class);

  private final OverviewRepository repository;

  public OverviewService(OverviewRepository repository) {
    this.repository = repository;
  }

  public List<ReadSimpleMetricDto> fetchRecentMetrics(int limit) {
    List<ReadSimpleMetricDto> results = repository.fetchRecentMetrics(limit);

    if (!results.isEmpty()) {
      for (ReadSimpleMetricDto result : results) {
        log.debug(result.normValue().toString());
      }
    } else {
      log.error("fetching worked but nothing found");
    }
    return results;
  }
}
