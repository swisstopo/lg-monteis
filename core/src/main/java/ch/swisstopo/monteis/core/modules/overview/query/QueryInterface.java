package ch.swisstopo.monteis.core.modules.overview.query;

import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;

public interface QueryInterface {

  /**
   * Fetches a List of {@link ReadSimpleMetricDto} entity.
   *
   * @param limit amount of data points to fetch
   * @return list of dto sorted by timestamp in descending order
   */
  List<ReadSimpleMetricDto> fetchRecentMetrics(int limit);
}
