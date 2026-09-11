package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorParameterDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SensorWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  // Explicit: Sensor's accessor is getDAS()/setDAS(), a JavaBean property named "DAS" (per
  // Introspector.decapitalize, a name starting with 2+ uppercase letters is left unchanged) -
  // it no longer matches WriteSensorDto's "das" record component by name.
  @Mapping(target = "DAS", source = "das")
  @Mapping(target = "mainExperiment", ignore = true)
  Sensor toDomain(WriteSensorDto dto);

  @AfterMapping
  default void mapMainExperimentReference(WriteSensorDto dto, @MappingTarget Sensor sensor) {
    if (dto.mainExperimentId() != null) {
      // The write path only ever needs the id (JooqSensorRepository persists
      // sensor.getMainExperiment().getId()); the read path repopulates a fully-hydrated
      // Experiment separately via ExperimentJooqMapper, so a bare id-holding shell is
      // sufficient and correct here.
      sensor.setMainExperiment(
          new Experiment(dto.mainExperimentId(), null, null, null, null, null));
    }
  }

  // New mapping for the nested parameters
  SensorParameter toDomain(WriteSensorParameterDto dto);

  // A null/omitted formula means the caller wants the default identity formula, not a missing
  // Formula - the frontend leaves it out entirely when the field is blank (see sensor-edit.ts),
  // relying on this default rather than sending an explicit "x". Written by hand instead of
  // generated: MapStruct's default null-handling would just propagate null here, leaving
  // SensorParameter.formula null and NPEing downstream (JooqSensorRepository,
  // SensorConfigPublisher).
  default Formula toDomain(WriteFormulaDto dto) {
    return dto == null ? new Formula() : new Formula(dto.expression());
  }

  Coordinates toDomain(CoordinatesDto dto);

  AlarmLimits toDomain(AlarmLimitsDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "das", source = "DAS")
  SensorResponseDto toDto(Sensor domain);

  // New mapping for the nested parameters
  SensorParameterResponseDto toDto(SensorParameter domain);

  FormulaResponseDto toDto(Formula domain);

  SensorTypeResponseDto toDto(SensorType domain);

  CoordinatesDto toDto(Coordinates domain);

  AlarmLimitsDto toDto(AlarmLimits domain);

  PagedResult<SensorResponseDto> toPagedDto(PagedResult<Sensor> pagedResult);
}
