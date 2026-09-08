package ch.swisstopo.monteis.core.modules.sensor.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorParameterRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorTypesRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SensorJooqMapper {

  // --- Parent Sensor Mappings ---

  @Mapping(target = "id", source = "sensorRecord.id")
  @Mapping(target = "name", source = "sensorRecord.name")
  @Mapping(target = "fulcrumId", source = "sensorRecord.fulcrumId")
  @Mapping(target = "version", source = "sensorRecord.version")
  @Mapping(target = "coordinates.x", source = "sensorRecord.x")
  @Mapping(target = "coordinates.y", source = "sensorRecord.y")
  @Mapping(target = "coordinates.z", source = "sensorRecord.z")
  @Mapping(target = "DAS", source = "sensorRecord.das")
  @Mapping(target = "mainExperiment", ignore = true)
  @Mapping(target = "parameters", ignore = true)
  Sensor toDomain(SensorsRecord sensorRecord);

  @Mapping(target = "x", source = "coordinates.x")
  @Mapping(target = "y", source = "coordinates.y")
  @Mapping(target = "z", source = "coordinates.z")
  @Mapping(target = "das", source = "DAS")
  @Mapping(target = "mainExperiment", ignore = true)
  SensorsRecord toRecord(Sensor domain);

  @Mapping(target = "x", source = "coordinates.x")
  @Mapping(target = "y", source = "coordinates.y")
  @Mapping(target = "z", source = "coordinates.z")
  @Mapping(target = "das", source = "DAS")
  @Mapping(target = "mainExperiment", ignore = true)
  void updateRecordFromDomain(Sensor sensor, @MappingTarget SensorsRecord sensorsRecord);

  // --- Embedded Sensor Parameter Mappings ---

  @Mapping(target = "id", source = "paramRecord.id")
  @Mapping(target = "name", source = "paramRecord.name")
  @Mapping(target = "unit", source = "paramRecord.unit")
  @Mapping(target = "active", source = "paramRecord.active")
  @Mapping(target = "comment", source = "paramRecord.comment")
  @Mapping(target = "alarmLimits.lower", source = "paramRecord.lowerAlarmLimit")
  @Mapping(target = "alarmLimits.upper", source = "paramRecord.upperAlarmLimit")
  @Mapping(target = "formula", source = "formulaRecord")
  @Mapping(target = "type", source = "typeRecord")
  SensorParameter toParameterDomain(
      SensorParameterRecord paramRecord,
      FormulasRecord formulaRecord,
      SensorTypesRecord typeRecord);

  @Mapping(target = "lowerAlarmLimit", source = "alarmLimits.lower")
  @Mapping(target = "upperAlarmLimit", source = "alarmLimits.upper")
  @Mapping(target = "formulaId", source = "formula.id")
  @Mapping(target = "typeId", source = "type.id")
  @Mapping(target = "sensorId", ignore = true)
  SensorParameterRecord toParameterRecord(SensorParameter domain);

  // formulaId/typeId are deliberately left for the caller to set explicitly (from the
  // found-or-created Formula/SensorType records) - the domain SensorParameter's own
  // formula.id/type.id are never populated by the write path (the frontend only ever sends a
  // formula expression / type name, not an id), so mapping them here would just overwrite the
  // fetched record's real, still-correct foreign keys with null.
  @Mapping(target = "lowerAlarmLimit", source = "alarmLimits.lower")
  @Mapping(target = "upperAlarmLimit", source = "alarmLimits.upper")
  @Mapping(target = "formulaId", ignore = true)
  @Mapping(target = "typeId", ignore = true)
  @Mapping(target = "sensorId", ignore = true)
  void updateParameterRecordFromDomain(
      SensorParameter domain, @MappingTarget SensorParameterRecord record);

  // --- Embedded Formula and Type Sub-Object Mappings ---

  Formula toDomain(FormulasRecord formulasRecord);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "expression", source = "expression")
  @Mapping(target = "version", source = "version")
  FormulasRecord toRecord(Formula domain);

  SensorType toDomain(SensorTypesRecord typesRecord);

  SensorTypesRecord toRecord(SensorType domain);
}
