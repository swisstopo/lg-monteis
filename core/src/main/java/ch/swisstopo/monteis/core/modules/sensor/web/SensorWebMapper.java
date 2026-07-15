package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.WriteSensorDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "bounds.lower", source = "lowerBound")
  @Mapping(target = "bounds.upper", source = "upperBound")
  Sensor toDomain(WriteSensorDto dto);

  Formula toDomain(WriteFormulaDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "lowerBound", source = "bounds.lower")
  @Mapping(target = "upperBound", source = "bounds.upper")
  SensorResponseDto toDto(Sensor domain);

  FormulaResponseDto toDto(Formula domain);
}
