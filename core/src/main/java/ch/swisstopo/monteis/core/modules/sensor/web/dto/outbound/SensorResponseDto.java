package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import java.util.UUID;

public record SensorResponseDto(
    UUID id,
    String code,
    String name,
    Unit unit,
    Integer fulcrumId,
    Experiment experiment,
    SensorTypeResponseDto type,
    String comment,
    CoordinatesDto coordinates,
    AlarmLimitsDto alarmLimits,
    Boolean active,
    FormulaResponseDto formula,
    Integer version) {}
