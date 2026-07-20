package ch.swisstopo.monteis.core.modules.experiment.web.dto;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import java.util.List;

public record ReadExperimentDetailsDto(
    Long id, String name, String description, Integer version, List<SensorResponseDto> sensors) {}
