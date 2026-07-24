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
  @Mapping(target = "bounds.lower", source = "sensorRecord.lowerBound")
  @Mapping(target = "bounds.upper", source = "sensorRecord.upperBound")
  @Mapping(
      target = "formula",
      source = "formulaRecord") // Automatically delegates to formula toDomain method below
  Sensor toDomain(SensorsRecord sensorRecord, FormulasRecord formulaRecord);

  @Mapping(target = "lowerBound", source = "bounds.lower")
  @Mapping(target = "upperBound", source = "bounds.upper")
  @Mapping(target = "formulaId", source = "formula.id")
  SensorsRecord toRecord(Sensor domain);

  @Mapping(target = "lowerBound", source = "bounds.lower")
  @Mapping(target = "upperBound", source = "bounds.upper")
  @Mapping(target = "formulaId", source = "formula.id")
  void updateRecordFromDomain(Sensor sensor, @MappingTarget SensorsRecord sensorsRecord);

  // --- Embedded Formula Sub-Object Mappings ---
  Formula toDomain(FormulasRecord formulasRecord);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "expression", source = "expression")
  @Mapping(target = "version", source = "version")
  FormulasRecord toRecord(Formula domain);
}
