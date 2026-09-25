package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.ExperimentWebMapper;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorParameterDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorTypeDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.*;
import java.time.LocalDate;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(
    componentModel = "spring",
    uses = ExperimentWebMapper.class,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
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

  /**
   * Turns an omitted formula into the identity formula ("x").
   *
   * <p>{@link WriteSensorParameterDto#formula()} is optional and documented as defaulting to "x",
   * and the webapp sends nothing at all when the field is left empty. MapStruct then writes a null
   * onto the parameter, and null is not a value the rest of the write path tolerates:
   * JooqSensorRepository dereferences the formula when persisting it, and
   * {@link SensorParameter#changeTriggersPublish} compares expressions to decide whether the Kafka
   * config has to be republished. Defaulting it in the domain constructor would not help - MapStruct
   * sets the field explicitly after construction - so the default belongs here, at the boundary
   * where "omitted" is still distinguishable from "cleared".
   */
  @AfterMapping
  default void defaultFormula(
      WriteSensorParameterDto dto, @MappingTarget SensorParameter parameter) {
    if (dto.formula() == null) {
      parameter.setFormula(new Formula());
    }
  }

  // id/version aren't provided by the write DTO: JooqSensorRepository resolves/creates the
  // SensorType by name (findOrCreateSensorTypeByName), so they're intentionally not mapped here.
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "version", ignore = true)
  SensorType toDomain(WriteSensorTypeDto dto);

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
  SensorResponseDto toDto(Sensor domain, @Context LocalDate today);

  // New mapping for the nested parameters
  SensorParameterResponseDto toDto(SensorParameter domain);

  FormulaResponseDto toDto(Formula domain);

  SensorTypeResponseDto toDto(SensorType domain);

  CoordinatesDto toDto(Coordinates domain);

  AlarmLimitsDto toDto(AlarmLimits domain);

  @Mapping(target = "das", source = "DAS")
  SensorDetailResponseDto toDetailDto(SensorDetail domain, @Context LocalDate today);

  SensorDetailParameterResponseDto toDetailDto(SensorDetailParameter domain);

  SensorParameterReadingResponseDto toDto(SensorParameterReading domain);
}
