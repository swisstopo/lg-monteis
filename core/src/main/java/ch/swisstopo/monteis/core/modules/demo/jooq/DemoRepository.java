package ch.swisstopo.monteis.core.modules.demo.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Formulas.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.RawSensorReading.RAW_SENSOR_READING;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;

import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import ch.swisstopo.monteis.core.modules.demo.web.dto.WriteSensorDto;
import java.util.List;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.exception.DataChangedException;
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

    // Step A: Validate the client's version against the DB baseline
    if (!sensorRecord.getVersion().equals(dto.version())
        || !formulaRecord.getVersion().equals(dto.formulaVersion())) {
      throw new DataChangedException("Concurrent modification detected.");
    }

    // Step B: Apply values only if they differ (to prevent Ghost Updates for JaVers)
    if (!Objects.equals(sensorRecord.getCode(), dto.code())) sensorRecord.setCode(dto.code());
    if (!Objects.equals(sensorRecord.getUpperBound(), dto.upperBound()))
      sensorRecord.setUpperBound(dto.upperBound());
    if (!Objects.equals(sensorRecord.getLowerBound(), dto.lowerBound()))
      sensorRecord.setLowerBound(dto.lowerBound());

    if (!Objects.equals(formulaRecord.getExpression(), dto.expression()))
      formulaRecord.setExpression(dto.expression());

    sensorRecord.update();
    formulaRecord.update();

    return new WriteSensorDto(
        dto.id(),
        sensorRecord.getCode(),
        sensorRecord.getUpperBound(),
        sensorRecord.getLowerBound(),
        sensorRecord.getVersion(),
        formulaRecord.getExpression(),
        formulaRecord.getVersion());
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

    return new WriteSensorDto(
        sensorRecord.getId(),
        sensorRecord.getCode(),
        sensorRecord.getUpperBound(),
        sensorRecord.getLowerBound(),
        sensorRecord.getVersion(),
        formulaRecord.getExpression(),
        formulaRecord.getVersion());
  }
}
