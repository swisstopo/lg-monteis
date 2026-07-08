package ch.swisstopo.monteis.core.modules.demo.web;

import ch.swisstopo.monteis.core.modules.demo.service.DemoService;
import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

  private final DemoService demoService;

  public DemoController(DemoService demoService) {
    this.demoService = demoService;
  }

  @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ReadSimpleMetric>> getMetrics(
      @RequestParam(defaultValue = "5") int limit) {

    List<ReadSimpleMetric> result = demoService.fetchRecentMetrics(limit);
    return ResponseEntity.ok(result);
  }

  @GetMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ReadSimpleMetric> getErrorDto(
      @RequestParam(defaultValue = "true") boolean testError) {
    ReadSimpleMetric result = demoService.fetchErrors(testError);
    return ResponseEntity.ok(result);
  }

  @GetMapping(value = "/exception", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ReadSimpleMetric> getExceptionDto(
      @RequestParam(defaultValue = "true") boolean testException) {
    ReadSimpleMetric result = demoService.fetchException(testException);
    return ResponseEntity.ok(result);
  }

  @PostMapping(
      value = "/sensors",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<WriteSensorDto> saveSensor(@RequestBody WriteSensorDto dto) {
    WriteSensorDto savedState = demoService.saveOrUpdateSensor(dto);
    return ResponseEntity.ok(savedState);
  }
}
