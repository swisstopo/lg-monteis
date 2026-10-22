package ch.swisstopo.monteis.pipeline.persistence;

import static org.jooq.impl.DSL.excluded;

import ch.swisstopo.monteis.pipeline.jooq.generated.Tables;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Row7;
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
            Tables.SENSOR_READING.DAS_KEY,
            Tables.SENSOR_READING.SENSOR_PARAMETER_ID,
            Tables.SENSOR_READING.RAW_VALUE,
            Tables.SENSOR_READING.NORM_VALUE,
            Tables.SENSOR_READING.VERSION,
            Tables.SENSOR_READING.STATUS);

    for (SensorReadingRecord sensorReadingRecord : dbRecords) {
      insertQuery =
          insertQuery.values(
              sensorReadingRecord.getTimestamp(),
              sensorReadingRecord.getDasKey(),
              sensorReadingRecord.getSensorParameterId(),
              sensorReadingRecord.getRawValue(),
              sensorReadingRecord.getNormValue(),
              sensorReadingRecord.getVersion(),
              sensorReadingRecord.getStatus());
    }

    insertQuery
        .onConflict(Tables.SENSOR_READING.TIMESTAMP, Tables.SENSOR_READING.DAS_KEY)
        .doUpdate()
        .set(
            Tables.SENSOR_READING.SENSOR_PARAMETER_ID,
            excluded(Tables.SENSOR_READING.SENSOR_PARAMETER_ID))
        .set(Tables.SENSOR_READING.RAW_VALUE, excluded(Tables.SENSOR_READING.RAW_VALUE))
        .set(Tables.SENSOR_READING.NORM_VALUE, excluded(Tables.SENSOR_READING.NORM_VALUE))
        .set(Tables.SENSOR_READING.VERSION, excluded(Tables.SENSOR_READING.VERSION))
        .set(Tables.SENSOR_READING.STATUS, excluded(Tables.SENSOR_READING.STATUS))
        .execute();
  }

  // --- Reprocessing backlog: two independent matches, run as separate scans rather than one
  // OR'd query (see HistoricalReadingChunkProcessor) to keep each one a plain index scan that
  // satisfies ORDER BY timestamp DESC for free, instead of forcing a BitmapOr + local Sort - the
  // exact regression sensor_reading_sensor_time_idx's comment (V1) already measured and designed
  // around for a single column.

  /**
   * Rows already tagged with this sensor_parameter_id - survives any number of past/future DAS-key
   * remaps, since the id never changes for a given sensor parameter.
   */
  public boolean checkOldSensorDataById(UUID sensorParameterId, short version) {
    return ctx.fetchExists(
        ctx.selectOne()
            .from(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_PARAMETER_ID.eq(sensorParameterId))
            .and(Tables.SENSOR_READING.VERSION.lt(version)));
  }

  /**
   * Rows ingested under the current DAS key before this parameter's config was ever known
   * (sensor_parameter_id still NULL) - the one-time backfill path.
   */
  public boolean checkOldSensorDataByDasKeyUnbackfilled(String dasKey, short version) {
    return ctx.fetchExists(
        ctx.selectOne()
            .from(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.DAS_KEY.eq(dasKey))
            .and(Tables.SENSOR_READING.SENSOR_PARAMETER_ID.isNull())
            .and(Tables.SENSOR_READING.VERSION.lt(version)));
  }

  // The cursor (previous chunk's oldest timestamp) lets each call seek straight to where the
  // last one left off via the matching (*, timestamp DESC) index, instead of re-scanning past
  // every already-reprocessed row on every chunk.
  public List<SensorReadingRecord> fetchOldSensorDataById(
      UUID sensorParameterId, short version, int limit, OffsetDateTime cursorTimestamp) {
    SelectConditionStep<SensorReadingRecord> query =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_PARAMETER_ID.eq(sensorParameterId))
            .and(Tables.SENSOR_READING.VERSION.lt(version));

    if (cursorTimestamp != null) {
      query = query.and(Tables.SENSOR_READING.TIMESTAMP.lt(cursorTimestamp));
    }

    return query.orderBy(Tables.SENSOR_READING.TIMESTAMP.desc()).limit(limit).fetch();
  }

  public List<SensorReadingRecord> fetchOldSensorDataByDasKeyUnbackfilled(
      String dasKey, short version, int limit, OffsetDateTime cursorTimestamp) {
    SelectConditionStep<SensorReadingRecord> query =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.DAS_KEY.eq(dasKey))
            .and(Tables.SENSOR_READING.SENSOR_PARAMETER_ID.isNull())
            .and(Tables.SENSOR_READING.VERSION.lt(version));

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

    Row7[] rows =
        records.stream()
            .map(
                r ->
                    DSL.row(
                        DSL.val(r.getTimestamp(), Tables.SENSOR_READING.TIMESTAMP),
                        DSL.val(r.getDasKey(), Tables.SENSOR_READING.DAS_KEY),
                        DSL.val(
                            r.getSensorParameterId(), Tables.SENSOR_READING.SENSOR_PARAMETER_ID),
                        DSL.val(r.getRawValue(), Tables.SENSOR_READING.RAW_VALUE),
                        DSL.val(r.getNormValue(), Tables.SENSOR_READING.NORM_VALUE),
                        DSL.val(r.getVersion(), Tables.SENSOR_READING.VERSION),
                        DSL.val(r.getStatus(), Tables.SENSOR_READING.STATUS)))
            .toArray(Row7[]::new);

    var v =
        DSL.values(rows)
            .as(
                "v",
                "timestamp",
                "das_key",
                "sensor_parameter_id",
                "raw_value",
                "norm_value",
                "version",
                "status");

    ctx.update(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.SENSOR_PARAMETER_ID, v.field("sensor_parameter_id", UUID.class))
        .set(Tables.SENSOR_READING.RAW_VALUE, v.field("raw_value", Double.class))
        .set(Tables.SENSOR_READING.NORM_VALUE, v.field("norm_value", Double.class))
        .set(Tables.SENSOR_READING.VERSION, v.field("version", Short.class))
        .set(Tables.SENSOR_READING.STATUS, v.field("status", RangeCategory.class))
        .from(v)
        .where(Tables.SENSOR_READING.TIMESTAMP.eq(v.field("timestamp", OffsetDateTime.class)))
        .and(Tables.SENSOR_READING.DAS_KEY.eq(v.field("das_key", String.class)))
        .execute();
  }
}
