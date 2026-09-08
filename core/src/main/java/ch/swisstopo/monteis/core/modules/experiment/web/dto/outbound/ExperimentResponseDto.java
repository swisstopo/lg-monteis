package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import java.util.UUID;

public record ExperimentResponseDto(
    UUID id,
    String name,
    String comment,
    PeriodDto period,
    Status status,
    Integer version,
    Integer sensorCount) {}
