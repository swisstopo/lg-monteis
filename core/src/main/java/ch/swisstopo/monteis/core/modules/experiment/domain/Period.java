package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.time.LocalDate;
import java.util.Map;

public record Period(LocalDate start, LocalDate end) {
  public Period {
    if (end.isBefore(start)) {
      throw new ObjectBusinessValidationException(
          "experiment.period.invalid", Map.of("end", end, "start", start));
    }
  }
}
