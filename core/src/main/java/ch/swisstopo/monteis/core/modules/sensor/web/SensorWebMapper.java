package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "alarmBounds.lower", source = "lowerAlarmBound")
  @Mapping(target = "alarmBounds.upper", source = "upperAlarmBound")
  @Mapping(target = "coordinates.xLocal", source = "xLocal")
  @Mapping(target = "coordinates.yLocal", source = "yLocal")
  @Mapping(target = "coordinates.zLocal", source = "zLocal")
  Sensor toDomain(WriteSensorDto dto);

  Formula toDomain(WriteFormulaDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "lowerAlarmBound", source = "alarmBounds.lower")
  @Mapping(target = "upperAlarmBound", source = "alarmBounds.upper")
  @Mapping(target = "xLocal", source = "coordinates.xLocal")
  @Mapping(target = "yLocal", source = "coordinates.yLocal")
  @Mapping(target = "zLocal", source = "coordinates.zLocal")
  SensorResponseDto toDto(Sensor domain);

  FormulaResponseDto toDto(Formula domain);
}
