package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import java.util.UUID;

/**
 * Sensor fields shared by every single-sensor response. The variants differ only in what their
 * {@code parameters} carry: {@link SensorResponseDto} the plain parameters, {@link
 * SensorDetailResponseDto} each parameter with its latest reading.
 */
public sealed interface SensorBaseResponseDto permits SensorResponseDto, SensorDetailResponseDto {

  UUID id();

  String name();

  String dasSensorAlias();

  Das das();

  UUID fulcrumId();

  ExperimentResponseDto mainExperiment();

  CoordinatesDto coordinates();

  Boolean active();

  String comment();

  Integer version();
}
