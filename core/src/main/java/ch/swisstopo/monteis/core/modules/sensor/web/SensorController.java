package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
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
  private final PagedRequestParser pagedRequestParser;

  public SensorController(
      SensorService service, SensorWebMapper mapper, PagedRequestParser pagedRequestParser) {
    this.service = service;
    this.mapper = mapper;
    this.pagedRequestParser = pagedRequestParser;
  }

  @Operation(summary = "Get a sensor by id", description = "Retrieves a sensor by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved formulas")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> getSensor(@PathVariable UUID id) {

    return ResponseEntity.ok(mapper.toDto(service.getSensor(id)));
  }

  @Operation(
      summary = "Create a new sensor",
      description =
          "Creates a new sensor in the system. The 'code' must be unique across all sensors.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "201", description = "Sensor successfully created")})
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> createSensor(
      @Validated(Create.class) @RequestBody WriteSensorDto dto) {

    Sensor createdSensor = service.createSensor(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(createdSensor));
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
    return ResponseEntity.status(HttpStatus.OK).body(mapper.toDto(updated));
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
      description = "Retrieves a page of sensors with optional sorting/filtering.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved sensors")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PagedResult<SensorResponseDto> getSensors(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    PagedResult<Sensor> result = service.getSensors(pagedRequestParser.parse(raw));
    return mapper.toPagedDto(result);
  }
}
