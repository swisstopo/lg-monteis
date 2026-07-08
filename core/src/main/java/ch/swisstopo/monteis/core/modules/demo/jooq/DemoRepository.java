package ch.swisstopo.monteis.core.modules.demo.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Formulas.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.RawSensorReading.RAW_SENSOR_READING;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
import java.util.List;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.exception.DataChangedException;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DemoRepository {

  private final DSLContext dsl;

  public DemoRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ReadSimpleMetric> fetchRecentMetrics(int limit) {
    return dsl.selectFrom(RAW_SENSOR_READING)
        .orderBy(RAW_SENSOR_READING.TIMESTAMP.desc())
        .limit(limit)
        .fetch()
        .map(
            r ->
                new ReadSimpleMetric(
                    r.getTimestamp(),
                    r.getSensorId(),
                    r.getRawValue(),
                    r.getNormValue(),
                    r.getVersion(),
                    r.getStatus()));
  }

  public WriteSensorDto saveSensorWithFormula(WriteSensorDto dto) {
    if (dto.id() == null) {
      return insertSensorWithFormula(dto);
    } else {
      return updateSensorWithFormula(dto);
    }
  }

  private @NonNull WriteSensorDto updateSensorWithFormula(WriteSensorDto dto) {
    var sensorRecord = dsl.selectFrom(SENSORS).where(SENSORS.ID.eq(dto.id())).fetchOne();
    var formulaRecord = dsl.selectFrom(FORMULAS).where(FORMULAS.SENSOR_ID.eq(dto.id())).fetchOne();

    if (sensorRecord == null || formulaRecord == null) {
      throw new DataChangedException("Record no longer exists.");
    }

    // 1. Rewind versions for Optimistic Locking
    sensorRecord.setVersion(dto.version());
    formulaRecord.setVersion(dto.formulaVersion());

    // 2. Apply the data
    sensorRecord.setCode(dto.code());
    sensorRecord.setUpperBound(dto.upperBound());
    sensorRecord.setLowerBound(dto.lowerBound());
    formulaRecord.setExpression(dto.expression());

    // 3. Update
    sensorRecord.update();
    formulaRecord.update();

    return toDto(sensorRecord, formulaRecord);
  }

  private @NonNull WriteSensorDto insertSensorWithFormula(WriteSensorDto dto) {
    var sensorRecord = dsl.newRecord(SENSORS);
    sensorRecord.setCode(dto.code());
    sensorRecord.setUpperBound(dto.upperBound());
    sensorRecord.setLowerBound(dto.lowerBound());
    sensorRecord.insert();

    var formulaRecord = dsl.newRecord(FORMULAS);
    formulaRecord.setSensorId(sensorRecord.getId());
    formulaRecord.setExpression(dto.expression());
    formulaRecord.insert();

    return toDto(sensorRecord, formulaRecord);
  }

  private static WriteSensorDto toDto(SensorsRecord sensorRecord, FormulasRecord formulaRecord) {
    return new WriteSensorDto(
        sensorRecord.getId(),
        sensorRecord.getCode(),
        sensorRecord.getUpperBound(),
        sensorRecord.getLowerBound(),
        sensorRecord.getVersion(),
        formulaRecord.getExpression(),
        formulaRecord.getVersion());
  }

  public Stream<WriteSensorDto> streamUnauditedSensors() {
    return dsl.select(SENSORS.fields())
        .select(FORMULAS.fields())
        .from(SENSORS)
        .join(FORMULAS)
        .on(SENSORS.ID.eq(FORMULAS.SENSOR_ID))
        .whereNotExists(
            dsl.selectOne()
                .from(DSL.table("jv_global_id"))
                // JaVers stores IDs as strings, so we cast it to match SENSORS.ID
                .where(DSL.field("local_id").cast(Long.class).eq(SENSORS.ID))
                // Ensure this matches your JaVers @TypeName or class name!
                .and(DSL.field("type_name").eq(WriteSensorDto.JAVERS_TYPE_NAME)))
        .fetchStream()
        .map(
            r -> {
              var sensorRecord = r.into(SENSORS);
              var formulaRecord = r.into(FORMULAS);
              return toDto(sensorRecord, formulaRecord);
            });
  }
}
