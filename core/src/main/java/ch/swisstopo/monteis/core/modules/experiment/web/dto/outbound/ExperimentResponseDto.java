package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.ExperimentDatesDto;

public record ExperimentResponseDto(
    Long id,
    String name,
    String comment,
    ExperimentDatesDto experimentDates,
    Status status,
    Integer version,
    Integer sensorCount) {}
