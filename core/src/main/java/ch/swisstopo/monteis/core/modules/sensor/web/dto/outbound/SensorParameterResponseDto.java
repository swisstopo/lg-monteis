package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.RangeLimitsDto;
import java.util.UUID;

public record SensorParameterResponseDto(
    UUID id,
    String name,
    String dasParameterAlias,
    SensorTypeResponseDto type,
    Unit unit,
    FormulaResponseDto formula,
    AlarmLimitsDto alarmLimits,
    RangeLimitsDto rangeLimits,
    Boolean notificationActive,
    Boolean closelyMonitored,
    AlarmLimitsDto specialAlarmLimits,
    Boolean active,
    String comment,
    Integer version) {}
