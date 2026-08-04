package ch.swisstopo.monteis.core.modules.sensor.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorTypesRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SensorJooqMapper {
  // --- Parent Sensor Graph Mapping ---
  @Mapping(target = "id", source = "sensorRecord.id")
  @Mapping(target = "name", source = "sensorRecord.name")
  @Mapping(target = "version", source = "sensorRecord.version")
  @Mapping(target = "alarmLimits.lower", source = "sensorRecord.lowerAlarmLimit")
  @Mapping(target = "alarmLimits.upper", source = "sensorRecord.upperAlarmLimit")
  @Mapping(target = "coordinates.x", source = "sensorRecord.x")
  @Mapping(target = "coordinates.y", source = "sensorRecord.y")
  @Mapping(target = "coordinates.z", source = "sensorRecord.z")
  @Mapping(
      target = "formula",
      source = "formulaRecord") // Automatically delegates to formula toDomain method below
  @Mapping(
      target = "type",
      source = "typeRecord") // Automatically delegates to type toDomain method below
  Sensor toDomain(
      SensorsRecord sensorRecord, FormulasRecord formulaRecord, SensorTypesRecord typeRecord);

  @Mapping(target = "lowerAlarmLimit", source = "alarmLimits.lower")
  @Mapping(target = "upperAlarmLimit", source = "alarmLimits.upper")
  @Mapping(target = "x", source = "coordinates.x")
  @Mapping(target = "y", source = "coordinates.y")
  @Mapping(target = "z", source = "coordinates.z")
  @Mapping(target = "formulaId", source = "formula.id")
  @Mapping(target = "typeId", source = "type.id")
  SensorsRecord toRecord(Sensor domain);

  @Mapping(target = "lowerAlarmLimit", source = "alarmLimits.lower")
  @Mapping(target = "upperAlarmLimit", source = "alarmLimits.upper")
  @Mapping(target = "x", source = "coordinates.x")
  @Mapping(target = "y", source = "coordinates.y")
  @Mapping(target = "z", source = "coordinates.z")
  @Mapping(target = "formulaId", source = "formula.id")
  @Mapping(target = "typeId", source = "type.id")
  void updateRecordFromDomain(Sensor sensor, @MappingTarget SensorsRecord sensorsRecord);

  // --- Embedded Formula and Type Sub-Object Mappings ---
  Formula toDomain(FormulasRecord formulasRecord);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "expression", source = "expression")
  @Mapping(target = "version", source = "version")
  FormulasRecord toRecord(Formula domain);

  SensorType toDomain(SensorTypesRecord typesRecord);

  SensorTypesRecord toRecord(SensorType domain);

  // --- Parent Sensor Graph Mapping (Read Flow) ---
  // Projects the joined jOOQ records straight into the response DTO, bypassing the Domain
  // entirely - the read flow never needs a Sensor instance.
  @Mapping(target = "id", source = "sensorRecord.id")
  @Mapping(target = "name", source = "sensorRecord.name")
  @Mapping(target = "version", source = "sensorRecord.version")
  @Mapping(target = "alarmLimits.lower", source = "sensorRecord.lowerAlarmLimit")
  @Mapping(target = "alarmLimits.upper", source = "sensorRecord.upperAlarmLimit")
  @Mapping(target = "coordinates.x", source = "sensorRecord.x")
  @Mapping(target = "coordinates.y", source = "sensorRecord.y")
  @Mapping(target = "coordinates.z", source = "sensorRecord.z")
  @Mapping(target = "formula", source = "formulaRecord")
  @Mapping(target = "type", source = "typeRecord")
  SensorResponseDto toDto(
      SensorsRecord sensorRecord, FormulasRecord formulaRecord, SensorTypesRecord typeRecord);

  FormulaResponseDto toDto(FormulasRecord formulasRecord);

  SensorTypeResponseDto toDto(SensorTypesRecord typesRecord);
}
