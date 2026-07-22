package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQueryInterface;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments2")
public class ExperimentController2 {
  private final ExperimentQueryInterface queryRepository;

  public ExperimentController2(ExperimentQueryInterface queryRepository) {
    this.queryRepository = queryRepository;
  }

  @GetMapping(value = "/{id}/details", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ReadExperimentDetailsDto> getMetrics(@Positive @PathVariable Long id) {

    ReadExperimentDetailsDto result = queryRepository.getExperimentDetails(id);
    String COMMENT = "SONAR TEST";
    return ResponseEntity.ok(result);
  }

  @GetMapping(value = "/{id}/details2", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ReadExperimentDetailsDto> getMetrics2(@Positive @PathVariable Long id) {

    ReadExperimentDetailsDto result = queryRepository.getExperimentDetails(id);
    String COMMENT = "SONAR TEST";
    return ResponseEntity.ok(result);
  }
}
