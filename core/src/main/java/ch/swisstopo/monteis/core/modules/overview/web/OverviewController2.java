package ch.swisstopo.monteis.core.modules.overview.web;

import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview2")
public class OverviewController2 {

  private final OverviewService overviewService;

  public OverviewController2(OverviewService overviewService) {
    this.overviewService = overviewService;
  }

  @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ReadSimpleMetricDto>> getMetrics(
      @RequestParam(defaultValue = "5") @Positive int limit) {

    List<ReadSimpleMetricDto> result = overviewService.fetchRecentMetrics(limit);
    return ResponseEntity.ok(result);
  }
}
