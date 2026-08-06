package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.ExperimentDatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import java.util.List;

public record ExperimentResponseDto(
    Long id,
    String name,
    String description,
    ExperimentDatesDto experimentDates,
    Status status,
    Integer version,
    List<SensorResponseDto> sensors) {}
