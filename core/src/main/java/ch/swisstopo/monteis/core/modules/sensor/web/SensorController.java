package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawExportRequest;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorParameterRowQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterRowResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {
  private final SensorService service;
  private final SensorWebMapper mapper;
  private final Clock clock;
  private final PagedRequestParser pagedRequestParser;
  private final SensorCsvExportQueryRepository csvExportQueryRepository;
  private final SensorParameterRowQueryRepository parameterRowQueryRepository;

  public SensorController(
      SensorService service,
      SensorWebMapper mapper,
      Clock clock,
      PagedRequestParser pagedRequestParser,
      SensorCsvExportQueryRepository csvExportQueryRepository,
      SensorParameterRowQueryRepository parameterRowQueryRepository) {
    this.service = service;
    this.mapper = mapper;
    this.clock = clock;
    this.pagedRequestParser = pagedRequestParser;
    this.csvExportQueryRepository = csvExportQueryRepository;
    this.parameterRowQueryRepository = parameterRowQueryRepository;
  }

  @Operation(summary = "Get a sensor by id", description = "Retrieves a sensor by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved formulas")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> getSensor(@PathVariable UUID id) {
    LocalDate today = LocalDate.now(clock);
    return ResponseEntity.ok(mapper.toDto(service.getSensor(id), today));
  }

  @Operation(
      summary = "Create a new sensor",
      description =
          "Creates a new sensor in the system. The 'dasSensorAlias' must be unique across all"
              + " sensors.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "201", description = "Sensor successfully created")})
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> createSensor(
      @Validated(Create.class) @RequestBody WriteSensorDto dto) {

    Sensor createdSensor = service.createSensor(mapper.toDomain(dto));
    LocalDate today = LocalDate.now(clock);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(createdSensor, today));
  }

  @Operation(
      summary = "Update an existing sensor",
      description =
          "Updates a sensor's mutable fields. Requires the correct ID and the current version"
              + " number for optimistic locking.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Sensor successfully updated")})
  @PutMapping(
      path = "{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> updateSensor(
      @PathVariable UUID id, @Validated(Update.class) @RequestBody WriteSensorDto dto) {
    if (!id.equals(dto.id())) {
      throw new ObjectBusinessValidationException(
          "id.validation.mismatch", Map.of("pathId", id, "id", dto.id()));
    }

    Sensor updated = service.updateSensor(mapper.toDomain(dto));
    LocalDate today = LocalDate.now(clock);
    return ResponseEntity.status(HttpStatus.OK).body(mapper.toDto(updated, today));
  }

  @Operation(
      summary = "Get all formulas",
      description =
          "Retrieves a list of all available formulas, sorted alphabetically by expression.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved formulas")
  @GetMapping(value = "/formulas", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FormulaResponseDto>> findAllFormulas() {
    return ResponseEntity.status(HttpStatus.OK)
        .body(service.findAllFormulas().stream().map(mapper::toDto).toList());
  }

  @Operation(
      summary = "Get all types",
      description = "Retrieves a list of all available types, sorted alphabetically by expression.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved types")
  @GetMapping(value = "/types", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SensorTypeResponseDto>> findAllTypes() {
    return ResponseEntity.status(HttpStatus.OK)
        .body(service.findAllTypes().stream().map(mapper::toDto).toList());
  }

  @Operation(
      summary = "Get sensors",
      description =
          "Retrieves a page of sensors at (Sensor, SensorParameter) grain - one row per parameter,"
              + " with a sensor with none appearing once with a null parameter - with optional"
              + " sorting/filtering.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved sensors")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PagedResult<SensorParameterRowResponseDto> getSensors(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    return parameterRowQueryRepository.findPaged(pagedRequestParser.parse(raw));
  }

  @Operation(
      summary = "Republish every sensor parameter's config",
      description =
          "One-time operational tool for MON-143's rollout: re-publishes every sensor parameter's"
              + " current config to internal-sensor-config, unconditionally, so the pipeline's"
              + " existing reprocessing flow backfills sensor_parameter_id on historical readings"
              + " ingested before each parameter's config was ever known.")
  @ApiResponse(responseCode = "202", description = "Republish triggered")
  @PostMapping(path = "/republish-config")
  public ResponseEntity<Void> republishAllActiveParameterConfigs() {
    service.republishAllActiveParameterConfigs();
    return ResponseEntity.accepted().build();
  }

  @Operation(
      summary = "Download sensors as CSV",
      description =
          "Streams all (sensor, sensor parameter) rows matching the optional sorting/filtering as"
              + " a CSV file - a sensor with no parameters still yields one row, a sensor with N"
              + " parameters yields N rows - capped at a server-configured maximum row count.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed sensors as CSV",
      content = @Content(mediaType = "text/csv"))
  @GetMapping(value = "/csv", produces = "text/csv")
  public void getSensorsCsv(
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel,
      HttpServletResponse response)
      throws IOException {
    PagedRequest exportRequest =
        pagedRequestParser.parseForExport(new RawExportRequest(sortModel, filterModel));

    response.setContentType("text/csv");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Content-Disposition", "attachment; filename=\"sensors.csv\"");

    Writer writer =
        new BufferedWriter(
            new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    csvExportQueryRepository.streamCsv(exportRequest, writer);
    writer.flush();
  }

  @Operation(
      summary = "Delete a sensor",
      description =
          "Deletes a sensor by its unique ID, removing its parameters and experiment links.")
  @ApiResponse(responseCode = "204", description = "Sensor successfully deleted")
  @DeleteMapping(path = "{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSensor(@PathVariable UUID id) {
    service.deleteSensor(id);
  }
}
