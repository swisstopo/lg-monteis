package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;

public record ExperimentResponseDto(
    Long id,
    String name,
    String comment,
    PeriodDto period,
    Status status,
    Integer version,
    Integer sensorCount) {}
