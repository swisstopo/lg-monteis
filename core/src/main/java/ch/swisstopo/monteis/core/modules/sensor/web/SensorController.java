package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.CreateSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.SensorResponseDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> createSensor(@Valid @RequestBody CreateSensorDto dto) {

    // 1. Map inbound JSON to Domain Model
    Sensor sensorToCreate = mapper.toDomain(dto);

    // 2. Execute business logic & insert (returns hydrated Domain Model)
    Sensor createdSensor = service.createSensor(sensorToCreate);

    // 3. Map the hydrated Domain Model back to JSON-friendly Response DTO
    SensorResponseDto responseBody = mapper.toDto(createdSensor);

    // 4. Return the full object to the client
    return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
  }

  @GetMapping(value = "/formulas", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Formula>> findAllFormulas() {
    return ResponseEntity.status(HttpStatus.OK).body(service.findAllFormulas());
  }
}
