package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQueryInterface;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
  private final ExperimentQueryInterface queryRepository;

  public ExperimentController(ExperimentQueryInterface queryRepository) {
    this.queryRepository = queryRepository;
  }

  @GetMapping(value = "/{id}/details", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ReadExperimentDetailsDto> getMetrics(@Positive @PathVariable Long id) {

    ReadExperimentDetailsDto result = queryRepository.getExperimentDetails(id);
    return ResponseEntity.ok(result);
  }
}
