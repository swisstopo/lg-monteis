package ch.swisstopo.monteis.core.modules.measurement.web;

import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;
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

  @GetMapping(value = "/charts/data", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ChartDataResponseDto>> getChartsData(
      @RequestParam @NotEmpty List<@Positive Long> ids,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime from,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime to) {
    return ResponseEntity.ok(measurementService.findMeasurements(ids, from, to));
  }
}
