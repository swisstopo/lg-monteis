package ch.swisstopo.monteis.core.modules.overview.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
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

  /**
   * Fetches one page of {@link ReadSimpleMetricDto} for the measurements table's infinite row
   * model, with the request's sorting/filtering applied.
   *
   * @param request the page (startRow/endRow) plus optional sort/filter model
   * @return the page's rows and the total number of rows matching the filter
   */
  PagedResult<ReadSimpleMetricDto> findPagedMetrics(PagedRequest request);
}
