package ch.swisstopo.monteis.core.modules.overview.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.modules.overview.service.OverviewService;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

  private final OverviewService overviewService;
  private final PagedRequestParser pagedRequestParser;

  public OverviewController(
      OverviewService overviewService, PagedRequestParser pagedRequestParser) {
    this.overviewService = overviewService;
    this.pagedRequestParser = pagedRequestParser;
  }

  @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ReadSimpleMetricDto>> getMetrics(
      @RequestParam(defaultValue = "5") @Positive int limit) {

    List<ReadSimpleMetricDto> result = overviewService.fetchRecentMetrics(limit);
    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "Get metrics",
      description = "Retrieves a page of sensor readings with optional sorting/filtering.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved metrics")
  @GetMapping(value = "/metrics/paged", produces = MediaType.APPLICATION_JSON_VALUE)
  public PagedResult<ReadSimpleMetricDto> getPagedMetrics(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    return overviewService.findPagedMetrics(pagedRequestParser.parse(raw));
  }
}
