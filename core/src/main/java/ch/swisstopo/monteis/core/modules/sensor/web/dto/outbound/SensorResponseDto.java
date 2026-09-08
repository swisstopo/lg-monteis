package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.DAS;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import java.util.List;
import java.util.UUID;

public record SensorResponseDto(
    UUID id,
    String name,
    String dasSensorAlias,
    DAS das,
    UUID fulcrumId,
    ExperimentResponseDto mainExperiment,
    CoordinatesDto coordinates,
    Boolean active,
    String comment,
    Integer version,
    List<SensorParameterResponseDto> parameters) {}
