package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import java.time.LocalDate;

public record ExperimentResponseDto(
    Long id,
    String name,
    String owner,
    String description,
    LocalDate experimentStart,
    LocalDate experimentEnd,
    Status status) {}
