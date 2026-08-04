package ch.swisstopo.monteis.core.modules.experiment.web.dto.nested;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ExperimentDatesDto(
    @NotNull LocalDate experimentStart, @NotNull LocalDate experimentEnd) {}
