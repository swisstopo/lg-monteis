package ch.swisstopo.monteis.pipeline.persistence;

import static org.jooq.impl.DSL.excluded;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.Tables;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class SensorReadingRepository {

  private final DSLContext ctx;

  public SensorReadingRepository(DSLContext ctx) {
    this.ctx = ctx;
  }

  public void upsertBatch(List<SensorReadingRecord> dbRecords) {
    if (dbRecords == null || dbRecords.isEmpty()) {
      return;
    }

    var insertQuery =
        ctx.insertInto(
            Tables.SENSOR_READING,
            Tables.SENSOR_READING.TIMESTAMP,
            Tables.SENSOR_READING.SENSOR_ID,
            Tables.SENSOR_READING.RAW_VALUE,
            Tables.SENSOR_READING.NORM_VALUE,
            Tables.SENSOR_READING.VERSION,
            Tables.SENSOR_READING.STATUS);

    for (SensorReadingRecord sensorReadingRecord : dbRecords) {
      insertQuery =
          insertQuery.values(
              sensorReadingRecord.getTimestamp(),
              sensorReadingRecord.getSensorId(),
              sensorReadingRecord.getRawValue(),
              sensorReadingRecord.getNormValue(),
              sensorReadingRecord.getVersion(),
              sensorReadingRecord.getStatus());
    }

    insertQuery
        .onConflict(Tables.SENSOR_READING.TIMESTAMP, Tables.SENSOR_READING.SENSOR_ID)
        .doUpdate()
        .set(Tables.SENSOR_READING.RAW_VALUE, excluded(Tables.SENSOR_READING.RAW_VALUE))
        .set(Tables.SENSOR_READING.NORM_VALUE, excluded(Tables.SENSOR_READING.NORM_VALUE))
        .set(Tables.SENSOR_READING.VERSION, excluded(Tables.SENSOR_READING.VERSION))
        .set(Tables.SENSOR_READING.STATUS, excluded(Tables.SENSOR_READING.STATUS))
        .execute();
  }

  public boolean checkOldSensorData(SensorConfig sensorConfig) {
    return ctx.fetchExists(
        ctx.selectOne()
            .from(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq(sensorConfig.getSensorId()))
            .and(Tables.SENSOR_READING.VERSION.lt(sensorConfig.getVersion().shortValue())));
  }

  public List<SensorReadingRecord> fetchOldSensorData(SensorConfig sensorConfig, int limit) {
    return ctx.selectFrom(Tables.SENSOR_READING)
        .where(Tables.SENSOR_READING.SENSOR_ID.eq(sensorConfig.getSensorId()))
        .and(Tables.SENSOR_READING.VERSION.lt(sensorConfig.getVersion().shortValue()))
        .limit(limit)
        .fetch();
  }

  public void bulkUpdate(List<SensorReadingRecord> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    // jOOQ natively translates this into an optimized bulk UPDATE statement
    ctx.batchUpdate(records).execute();
  }
}
