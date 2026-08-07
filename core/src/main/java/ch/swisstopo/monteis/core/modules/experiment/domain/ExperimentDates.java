package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.time.LocalDate;
import java.util.Map;

public record ExperimentDates(LocalDate experimentStart, LocalDate experimentEnd) {
  public ExperimentDates {
    if (experimentEnd.isBefore(experimentStart)) {
      throw new ObjectBusinessValidationException(
          "sensor.experimentDates.invalid",
          Map.of("experimentEnd", experimentEnd, "experimentStart", experimentStart));
    }
  }
}
