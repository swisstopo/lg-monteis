package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.CreateFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.CreateSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor.SensorResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "bounds.lower", source = "lowerBound") // FIX: Target field path is lower
  @Mapping(target = "bounds.upper", source = "upperBound") // FIX: Target field path is upper
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  Sensor toDomain(CreateSensorDto dto);

  // FIX: Clear the unmapped target warning by ignoring database-managed keys on creation DTOs
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  Formula toDomain(CreateFormulaDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "lowerBound", source = "bounds.lower") // FIX: Source field path is lower
  @Mapping(target = "upperBound", source = "bounds.upper") // FIX: Source field path is upper
  SensorResponseDto toDto(Sensor domain);

  FormulaResponseDto toDto(Formula domain);
}
