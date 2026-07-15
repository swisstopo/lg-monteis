package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.WriteSensorDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensors")
public class SensorController {
  private final SensorService service;
  private final SensorWebMapper mapper;

  public SensorController(SensorService service, SensorWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
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
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> updateSensor(
      @Validated(Update.class) @RequestParam Long id, @RequestBody WriteSensorDto dto) {
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
}
