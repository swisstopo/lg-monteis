package ch.swisstopo.monteis.core.modules.measurement.web;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvDownload;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawExportRequest;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

  /** A larger measurements export is refused, the user narrows the table down first. */
  private static final int CSV_MAX_ROWS = 1000;

  private final MeasurementService measurementService;
  private final PagedRequestParser pagedRequestParser;
  private final Clock clock;
  private final MeasurementCsvExportQueryRepository csvExportQueryRepository;

  public MeasurementController(
      MeasurementService measurementService,
      PagedRequestParser pagedRequestParser,
      Clock clock,
      MeasurementCsvExportQueryRepository csvExportQueryRepository) {
    this.measurementService = measurementService;
    this.pagedRequestParser = pagedRequestParser;
    this.clock = clock;
    this.csvExportQueryRepository = csvExportQueryRepository;
  }

  /**
   * Returns one sensor's series at full resolution.
   *
   * <p>One sensor per request on purpose. The database cost is linear in rows either way — a
   * three-sensor query measured the same per-row cost as three single-sensor queries — so batching
   * saves no work, it only serializes it. Split into separate requests the client can run them
   * concurrently, paint each series as it lands, and cache them independently; peak heap per
   * request drops by the same factor.
   */
  @GetMapping(value = "/charts/data", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ChartDataResponseDto> getChartData(
      @RequestParam @NotNull UUID id,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime from,
      @RequestParam @NotNull @PastOrPresent OffsetDateTime to) {
    return measurementService
        .findChartData(id, from, to)
        .map(ResponseEntity::ok)
        // a sensor parameter RLS hides looks exactly like a missing one: 404 object.not-found
        .orElseThrow(() -> new ObjectNotFoundException(SensorParameter.class));
  }

  @Operation(
      summary = "Get sensor details by id",
      description =
          "Retrieves a sensor with all its parameters, each including its latest reading.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved sensor details")
  @GetMapping(path = "/sensors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorDetailResponseDto> getSensorDetail(@PathVariable UUID id) {
    return ResponseEntity.ok(measurementService.getSensorDetail(id, LocalDate.now(clock)));
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PagedResult<MeasurementResponseDto>> getMeasurements(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    PagedResult<MeasurementResponseDto> measurements =
        measurementService.getPagedMeasurements(pagedRequestParser.parse(raw));
    return ResponseEntity.ok(measurements);
  }

  @Operation(
      summary = "Download measurements as CSV",
      description =
          "Streams all measurement rows (one per sensor parameter, with its latest reading)"
              + " matching the optional sorting/filtering as a CSV file. Refused with"
              + " export.tooManyRows when more rows match than the server-configured maximum.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed measurements as CSV",
      content =
          @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
  @GetMapping(value = "/csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> getMeasurementsCsv(
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    PagedRequest exportRequest =
        pagedRequestParser.parseForExport(
            new RawExportRequest(sortModel, filterModel), CSV_MAX_ROWS);
    return CsvDownload.attachment(
        "measurements.csv", writer -> csvExportQueryRepository.streamCsv(exportRequest, writer));
  }
}
