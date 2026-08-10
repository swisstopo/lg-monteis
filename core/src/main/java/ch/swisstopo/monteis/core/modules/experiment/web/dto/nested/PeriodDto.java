package ch.swisstopo.monteis.core.modules.experiment.web.dto.nested;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PeriodDto(@NotNull LocalDate start, @NotNull LocalDate end) {}
