package ch.swisstopo.monteis.core.modules.sensor.jooq;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SensorJooqMapper {
  // --- Parent Sensor Graph Mapping ---
  @Mapping(target = "id", source = "sensorRecord.id")
  @Mapping(target = "version", source = "sensorRecord.version")
  @Mapping(target = "alarmBounds.lower", source = "sensorRecord.lowerAlarmBound")
  @Mapping(target = "alarmBounds.upper", source = "sensorRecord.upperAlarmBound")
  @Mapping(target = "coordinates.xLocal", source = "sensorRecord.XLocal")
  @Mapping(target = "coordinates.yLocal", source = "sensorRecord.YLocal")
  @Mapping(target = "coordinates.zLocal", source = "sensorRecord.ZLocal")
  @Mapping(
      target = "formula",
      source = "formulaRecord") // Automatically delegates to formula toDomain method below
  Sensor toDomain(SensorsRecord sensorRecord, FormulasRecord formulaRecord);

  @Mapping(target = "lowerAlarmBound", source = "alarmBounds.lower")
  @Mapping(target = "upperAlarmBound", source = "alarmBounds.upper")
  @Mapping(target = "XLocal", source = "coordinates.xLocal")
  @Mapping(target = "YLocal", source = "coordinates.yLocal")
  @Mapping(target = "ZLocal", source = "coordinates.zLocal")
  @Mapping(target = "formulaId", source = "formula.id")
  SensorsRecord toRecord(Sensor domain);

  @Mapping(target = "lowerAlarmBound", source = "alarmBounds.lower")
  @Mapping(target = "upperAlarmBound", source = "alarmBounds.upper")
  @Mapping(target = "XLocal", source = "coordinates.xLocal")
  @Mapping(target = "YLocal", source = "coordinates.yLocal")
  @Mapping(target = "ZLocal", source = "coordinates.zLocal")
  @Mapping(target = "formulaId", source = "formula.id")
  void updateRecordFromDomain(Sensor sensor, @MappingTarget SensorsRecord record);

  // --- Embedded Formula Sub-Object Mappings ---
  Formula toDomain(FormulasRecord record);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "expression", source = "expression")
  @Mapping(target = "version", source = "version")
  FormulasRecord toRecord(Formula domain);
}
