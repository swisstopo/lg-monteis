package ch.swisstopo.monteis.pipeline.persistence;

import static org.jooq.impl.DSL.excluded;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.Tables;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Row6;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
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

  // The cursor (previous chunk's oldest timestamp) lets each call seek straight to where the
  // last one left off via the sensor_reading_sensor_time_idx (sensor_id, timestamp DESC) index,
  // instead of re-scanning past every already-reprocessed row for this sensor on every chunk.
  public List<SensorReadingRecord> fetchOldSensorData(
      SensorConfig sensorConfig, int limit, OffsetDateTime cursorTimestamp) {
    SelectConditionStep<SensorReadingRecord> query =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq(sensorConfig.getSensorId()))
            .and(Tables.SENSOR_READING.VERSION.lt(sensorConfig.getVersion().shortValue()));

    if (cursorTimestamp != null) {
      query = query.and(Tables.SENSOR_READING.TIMESTAMP.lt(cursorTimestamp));
    }

    return query.orderBy(Tables.SENSOR_READING.TIMESTAMP.desc()).limit(limit).fetch();
  }

  // A single set-based UPDATE ... FROM (VALUES ...) statement, not ctx.batchUpdate(records)
  // (JDBC-batched, but still one UPDATE per row under the hood). DSL.val(value, FIELD) pins
  // each cell to its column's real data type so Postgres can type the VALUES rows (needed for
  // the range_category enum column, and because NORM_VALUE can be null with no other type hint).
  public void bulkUpdate(List<SensorReadingRecord> records) {
    if (records == null || records.isEmpty()) {
      return;
    }

    Row6[] rows =
        records.stream()
            .map(
                r ->
                    DSL.row(
                        DSL.val(r.getTimestamp(), Tables.SENSOR_READING.TIMESTAMP),
                        DSL.val(r.getSensorId(), Tables.SENSOR_READING.SENSOR_ID),
                        DSL.val(r.getRawValue(), Tables.SENSOR_READING.RAW_VALUE),
                        DSL.val(r.getNormValue(), Tables.SENSOR_READING.NORM_VALUE),
                        DSL.val(r.getVersion(), Tables.SENSOR_READING.VERSION),
                        DSL.val(r.getStatus(), Tables.SENSOR_READING.STATUS)))
            .toArray(Row6[]::new);

    var v =
        DSL.values(rows)
            .as("v", "timestamp", "sensor_id", "raw_value", "norm_value", "version", "status");

    ctx.update(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.RAW_VALUE, v.field("raw_value", Double.class))
        .set(Tables.SENSOR_READING.NORM_VALUE, v.field("norm_value", Double.class))
        .set(Tables.SENSOR_READING.VERSION, v.field("version", Short.class))
        .set(Tables.SENSOR_READING.STATUS, v.field("status", RangeCategory.class))
        .from(v)
        .where(Tables.SENSOR_READING.TIMESTAMP.eq(v.field("timestamp", OffsetDateTime.class)))
        .and(Tables.SENSOR_READING.SENSOR_ID.eq(v.field("sensor_id", String.class)))
        .execute();
  }
}
