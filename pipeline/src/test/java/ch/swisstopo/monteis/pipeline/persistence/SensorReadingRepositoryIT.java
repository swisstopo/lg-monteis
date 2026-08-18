package ch.swisstopo.monteis.pipeline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.ITConfiguration.IT;
import ch.swisstopo.monteis.pipeline.jooq.generated.Tables;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IT
class SensorReadingRepositoryIT {

  @Autowired SensorReadingRepository repo;

  @Autowired DSLContext ctx;

  @BeforeEach
  void cleanup() {
    ctx.deleteFrom(Tables.SENSOR_READING).execute();
  }

  @Test
  void upsertBatch_should_upsert_records() {
    // given
    var ts = OffsetDateTime.parse("2026-06-24T10:00:00Z");

    var first =
        new SensorReadingRecord(ts, "sensor-001", 10.0, 9.0, (short) 1, RangeCategory.correct);

    // when
    repo.upsertBatch(List.of(first));

    var row =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-001"))
            .fetchOne();

    // then
    assertThat(row).isNotNull();
    assertThat(row.getRawValue()).isEqualTo(10.0);

    // given
    var updated =
        new SensorReadingRecord(ts, "sensor-001", 99.0, 88.0, (short) 2, RangeCategory.too_high);

    // when
    repo.upsertBatch(List.of(updated));

    var after =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-001"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts))
            .fetchOne();

    // then
    assertThat(after).isNotNull();
    assertThat(after.getRawValue()).isEqualTo(99.0);
    assertThat(after.getVersion()).isEqualTo((short) 2);
    assertThat(after.getStatus()).isEqualTo(RangeCategory.too_high);
  }

  @Test
  void upsertBatch_should_insert_multiple_records() {
    // given
    var records =
        List.of(
            new SensorReadingRecord(
                OffsetDateTime.parse("2026-06-24T10:00:00Z"),
                "sensor-001",
                10.0,
                9.0,
                (short) 1,
                RangeCategory.correct),
            new SensorReadingRecord(
                OffsetDateTime.parse("2026-06-24T10:05:00Z"),
                "sensor-002",
                11.0,
                10.0,
                (short) 1,
                RangeCategory.correct));

    // when
    repo.upsertBatch(records);

    var all = ctx.selectFrom(Tables.SENSOR_READING).fetch();

    // then
    assertThat(all).hasSize(2);
  }

  @Test
  void upsertBatch_should_do_nothing_when_input_is_null() {
    repo.upsertBatch(null);

    var count = ctx.fetchCount(Tables.SENSOR_READING);

    assertThat(count).isZero();
  }

  @Test
  void upsertBatch_should_do_nothing_when_input_is_empty() {
    repo.upsertBatch(List.of());

    var count = ctx.fetchCount(Tables.SENSOR_READING);

    assertThat(count).isZero();
  }

  @Test
  void checkOldSensorData_should_detect_old_sensor_data() {
    // given
    var config = new SensorConfig("sensor-001", "x + 1", 100.0, 0.0, 5);

    ctx.insertInto(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.SENSOR_ID, "sensor-001")
        .set(Tables.SENSOR_READING.VERSION, (short) 3)
        .set(Tables.SENSOR_READING.TIMESTAMP, OffsetDateTime.now())
        .set(Tables.SENSOR_READING.RAW_VALUE, 1.0)
        .set(Tables.SENSOR_READING.NORM_VALUE, 1.0)
        .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
        .execute();

    // when
    boolean result = repo.checkOldSensorData(config);

    // then
    assertTrue(result);
  }

  @Test
  void checkOldSensorData_should_return_false_when_no_old_data_exists() {
    // given
    var config = new SensorConfig("sensor-999", "x + 1", 100.0, 0.0, 5);

    // when
    boolean result = repo.checkOldSensorData(config);

    // then
    assertFalse(result);
  }

  @Test
  void fetchOldSensorData_should_fetch_only_data_within_limit() {
    // given
    var sensorId = "sensor-001";
    for (int i = 1; i <= 5; i++) {
      ctx.insertInto(Tables.SENSOR_READING)
          .set(Tables.SENSOR_READING.SENSOR_ID, sensorId)
          .set(Tables.SENSOR_READING.VERSION, (short) i)
          .set(Tables.SENSOR_READING.TIMESTAMP, OffsetDateTime.now().plusMinutes(i))
          .set(Tables.SENSOR_READING.RAW_VALUE, 1.0)
          .set(Tables.SENSOR_READING.NORM_VALUE, 1.0)
          .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
          .execute();
    }
    var config = new SensorConfig(sensorId, "x + 1", 100.0, 0.0, 4);

    // when
    var result = repo.fetchOldSensorData(config, 2, null);

    // then
    assertThat(result).hasSize(2).allMatch(r -> r.getVersion() < 4);
  }

  @Test
  void fetchOldSensorData_should_resume_from_cursor_without_gaps_or_duplicates() {
    // given
    var sensorId = "sensor-001";
    // Postgres TIMESTAMPTZ has microsecond precision, so truncate before inserting — otherwise
    // the nanosecond-precision in-memory value never equals what's read back from the DB.
    var baseTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    for (int i = 1; i <= 5; i++) {
      ctx.insertInto(Tables.SENSOR_READING)
          .set(Tables.SENSOR_READING.SENSOR_ID, sensorId)
          .set(Tables.SENSOR_READING.VERSION, (short) 1)
          .set(Tables.SENSOR_READING.TIMESTAMP, baseTime.plusMinutes(i))
          .set(Tables.SENSOR_READING.RAW_VALUE, 1.0)
          .set(Tables.SENSOR_READING.NORM_VALUE, 1.0)
          .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
          .execute();
    }
    var config = new SensorConfig(sensorId, "x + 1", 100.0, 0.0, 2);

    // when: first chunk (newest first, no cursor yet)
    var firstChunk = repo.fetchOldSensorData(config, 2, null);
    // then
    assertThat(firstChunk).hasSize(2);
    assertThat(firstChunk.get(0).getTimestamp()).isEqualTo(baseTime.plusMinutes(5));
    assertThat(firstChunk.get(1).getTimestamp()).isEqualTo(baseTime.plusMinutes(4));

    // when: second chunk resumes strictly before the first chunk's oldest timestamp
    var secondChunk = repo.fetchOldSensorData(config, 2, firstChunk.getLast().getTimestamp());
    // then
    assertThat(secondChunk).hasSize(2);
    assertThat(secondChunk.get(0).getTimestamp()).isEqualTo(baseTime.plusMinutes(3));
    assertThat(secondChunk.get(1).getTimestamp()).isEqualTo(baseTime.plusMinutes(2));

    // when: third chunk picks up the single remaining row
    var thirdChunk = repo.fetchOldSensorData(config, 2, secondChunk.getLast().getTimestamp());
    // then
    assertThat(thirdChunk).hasSize(1);
    assertThat(thirdChunk.getFirst().getTimestamp()).isEqualTo(baseTime.plusMinutes(1));
  }

  @Test
  void bulkUpdate_should_update_records() {
    // given
    var ts = OffsetDateTime.parse("2026-06-24T10:00:00Z");
    ctx.insertInto(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.SENSOR_ID, "sensor-bulk-001")
        .set(Tables.SENSOR_READING.TIMESTAMP, ts)
        .set(Tables.SENSOR_READING.VERSION, (short) 1)
        .set(Tables.SENSOR_READING.RAW_VALUE, 1.0)
        .set(Tables.SENSOR_READING.NORM_VALUE, 1.0)
        .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
        .execute();

    var updated =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-001"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts))
            .fetchOne();

    assertThat(updated).isNotNull();

    updated.setRawValue(999.0);
    updated.setNormValue(888.0);

    // when
    repo.bulkUpdate(List.of(updated));

    var after =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-001"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts))
            .fetchOne();

    // then
    assertThat(after).isNotNull();
    assertThat(after.getRawValue()).isEqualTo(999.0);
    assertThat(after.getNormValue()).isEqualTo(888.0);
  }

  @Test
  void bulkUpdate_should_update_multiple_records_with_a_null_norm_value() {
    // given: mirrors the poison-pill reprocessing path, which sets norm_value to null
    var ts1 = OffsetDateTime.parse("2026-06-24T11:00:00Z");
    var ts2 = OffsetDateTime.parse("2026-06-24T11:05:00Z");
    ctx.insertInto(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.SENSOR_ID, "sensor-bulk-002")
        .set(Tables.SENSOR_READING.TIMESTAMP, ts1)
        .set(Tables.SENSOR_READING.VERSION, (short) 1)
        .set(Tables.SENSOR_READING.RAW_VALUE, -999.0)
        .set(Tables.SENSOR_READING.NORM_VALUE, 5.0)
        .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
        .execute();
    ctx.insertInto(Tables.SENSOR_READING)
        .set(Tables.SENSOR_READING.SENSOR_ID, "sensor-bulk-002")
        .set(Tables.SENSOR_READING.TIMESTAMP, ts2)
        .set(Tables.SENSOR_READING.VERSION, (short) 1)
        .set(Tables.SENSOR_READING.RAW_VALUE, 10.0)
        .set(Tables.SENSOR_READING.NORM_VALUE, 20.0)
        .set(Tables.SENSOR_READING.STATUS, RangeCategory.correct)
        .execute();

    var poisonPill =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-002"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts1))
            .fetchOne();
    var healthy =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-002"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts2))
            .fetchOne();
    assertThat(poisonPill).isNotNull();
    assertThat(healthy).isNotNull();

    poisonPill.setVersion((short) 3);
    poisonPill.setNormValue(null);
    poisonPill.setStatus(RangeCategory.too_low);

    healthy.setNormValue(21.0);
    healthy.setVersion((short) 3);
    healthy.setStatus(RangeCategory.too_high);

    // when
    repo.bulkUpdate(List.of(poisonPill, healthy));

    // then
    var afterPoison =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-002"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts1))
            .fetchOne();
    var afterHealthy =
        ctx.selectFrom(Tables.SENSOR_READING)
            .where(Tables.SENSOR_READING.SENSOR_ID.eq("sensor-bulk-002"))
            .and(Tables.SENSOR_READING.TIMESTAMP.eq(ts2))
            .fetchOne();

    assertThat(afterPoison).isNotNull();
    assertThat(afterPoison.getNormValue()).isNull();
    assertThat(afterPoison.getVersion()).isEqualTo((short) 3);
    assertThat(afterPoison.getStatus()).isEqualTo(RangeCategory.too_low);
    assertThat(afterPoison.getRawValue()).isEqualTo(-999.0);

    assertThat(afterHealthy).isNotNull();
    assertThat(afterHealthy.getNormValue()).isEqualTo(21.0);
    assertThat(afterHealthy.getVersion()).isEqualTo((short) 3);
    assertThat(afterHealthy.getStatus()).isEqualTo(RangeCategory.too_high);
  }

  @Test
  void bulkUpdate_should_do_nothing_when_input_is_null() {
    repo.bulkUpdate(null);

    var count = ctx.fetchCount(Tables.SENSOR_READING);

    assertThat(count).isZero();
  }

  @Test
  void bulkUpdate_should_do_nothing_when_input_is_empty() {
    repo.bulkUpdate(List.of());

    var count = ctx.fetchCount(Tables.SENSOR_READING);

    assertThat(count).isZero();
  }
}
