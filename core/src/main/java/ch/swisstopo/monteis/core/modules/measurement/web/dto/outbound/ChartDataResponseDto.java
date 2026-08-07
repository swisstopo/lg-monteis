package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.util.List;

public record ChartDataResponseDto(
    Long id, String sensorCode, String sensorName, Unit unit, List<ChartPointDto> data) {}
