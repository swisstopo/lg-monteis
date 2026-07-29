package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  Sensor toDomain(WriteSensorDto dto);

  Formula toDomain(WriteFormulaDto dto);

  Coordinates toDomain(CoordinatesDto dto);

  @Mapping(target = "lower", source = "lowerBound")
  @Mapping(target = "upper", source = "upperBound")
  AlarmLimits toDomain(AlarmLimitsDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  SensorResponseDto toDto(Sensor domain);

  FormulaResponseDto toDto(Formula domain);

  CoordinatesDto toDto(Coordinates domain);

  @Mapping(target = "lowerBound", source = "lower")
  @Mapping(target = "upperBound", source = "upper")
  AlarmLimitsDto toDto(AlarmLimits domain);
}
