package ch.swisstopo.monteis.core.modules.overview.web;

import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

  private final OverviewService overviewService;

  public OverviewController(OverviewService overviewService) {
    this.overviewService = overviewService;
  }

  @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ReadSimpleMetricDto>> getMetrics(
      @RequestParam(defaultValue = "5") int limit) {

    List<ReadSimpleMetricDto> result = overviewService.fetchRecentMetrics(limit);
    return ResponseEntity.ok(result);
  }
}
