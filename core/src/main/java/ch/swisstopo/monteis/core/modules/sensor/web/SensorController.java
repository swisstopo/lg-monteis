package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.WriteSensorDto;
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

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> createSensor(
      @Validated(Create.class) @RequestBody WriteSensorDto dto) {

    Sensor sensorToCreate = mapper.toDomain(dto);
    Sensor createdSensor = service.createSensor(sensorToCreate);
    SensorResponseDto responseBody = mapper.toDto(createdSensor);
    return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
  }

  @PutMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SensorResponseDto> updateSensor(
      @Validated(Update.class) @RequestBody WriteSensorDto dto) {
    Sensor sensorToUpdate = mapper.toDomain(dto);
    Sensor updated = service.updateSensor(sensorToUpdate);
    SensorResponseDto result = mapper.toDto(updated);
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping(value = "/formulas", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Formula>> findAllFormulas() {
    return ResponseEntity.status(HttpStatus.OK).body(service.findAllFormulas());
  }
}
