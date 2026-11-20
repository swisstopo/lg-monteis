package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUserDto;
import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.query.OwnersStatus;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import java.util.List;
import java.util.UUID;

public record ExperimentResponseDto(
    UUID id,
    String name,
    String comment,
    PeriodDto period,
    Status status,
    Integer version,
    Integer sensorCount,
    List<DirectoryUserDto> owners,
    OwnersStatus ownersStatus,
    List<UUID> organisationIds) {}
