package ch.swisstopo.monteis.core.modules.measurement.web;

import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

  private final MeasurementService measurementService;

  public MeasurementController(MeasurementService measurementService) {
    this.measurementService = measurementService;
  }

  /**
   * Returns one sensor's series at full resolution.
   *
   * <p>One sensor per request on purpose. The database cost is linear in rows either way — a
   * three-sensor query measured the same per-row cost as three single-sensor queries — so batching
   * saves no work, it only serialises it. Split into separate requests the client can run them
   * concurrently, paint each series as it lands, and cache them independently; peak heap per
   * request drops by the same factor.
   */
  @GetMapping(value = "/charts/data", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ChartDataResponseDto> getChartData(
      @RequestParam @NotNull UUID id,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime from,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime to) {
    return measurementService
        .findMeasurements(id, from, to)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
