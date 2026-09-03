package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  Sensor toDomain(WriteSensorDto dto);

  Formula toDomain(WriteFormulaDto dto);

  Coordinates toDomain(CoordinatesDto dto);

  AlarmLimits toDomain(AlarmLimitsDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  SensorResponseDto toDto(Sensor domain);

  FormulaResponseDto toDto(Formula domain);

  SensorTypeResponseDto toDto(SensorType domain);

  CoordinatesDto toDto(Coordinates domain);

  AlarmLimitsDto toDto(AlarmLimits domain);

  PagedResult<SensorResponseDto> toPagedDto(PagedResult<Sensor> pagedResult);
}
