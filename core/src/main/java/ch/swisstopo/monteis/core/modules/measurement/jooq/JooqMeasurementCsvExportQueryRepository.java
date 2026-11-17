package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.LatestReadingProjections.DAS_KEY;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.LatestReadingProjections.LATEST_READINGS;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.exception.TooManyExportRowsException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementCsvExportQueryRepository;
import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes all measurement rows (one per sensor parameter, with its latest reading) matching a
 * filter/sort as CSV, as raw data for further processing in other programs, like the sensor and
 * experiment exports: the grid's column ids as header, ISO-8601 timestamps, numbers in full
 * precision and enum names. The display-only trend column is left out, the measurement status the
 * table only shows as a cell color is written out.
 *
 * <p>Same row grain, join, filter/sort semantics and default sort as {@link
 * MeasurementQueryRepository#findPaged} (the grid's backing query). The rows are fetched before the
 * first one is written, so an export matching more rows than its limit is refused with {@link
 * TooManyExportRowsException} (and a failing query answers with an error) instead of a cut off
 * file. Writing still happens inside this {@code @Transactional(readOnly = true)} method, so the DB
 * connection is held until the client has read the whole file, as in the other exports.
 */
@Repository
@Transactional(readOnly = true)
public class JooqMeasurementCsvExportQueryRepository
    implements MeasurementCsvExportQueryRepository {

  private static final List<String> HEADER =
      List.of(
          "experimentName",
          "dasKey",
          "sensorName",
          "sensorParameterName",
          "sensorType",
          "newestMeasurement",
          "measureValue",
          "measurementStatus",
          "unit",
          "alarmLimits.lower",
          "alarmLimits.upper",
          "active",
          "coordinates.x",
          "coordinates.y",
          "coordinates.z",
          "comment");

  private static final Field<OffsetDateTime> NEWEST_MEASUREMENT =
      LATEST_READINGS.field(SENSOR_READING_SECURED.TIMESTAMP);
  private static final Field<Double> MEASURE_VALUE =
      LATEST_READINGS.field(SENSOR_READING_SECURED.NORM_VALUE);
  private static final Field<String> MEASUREMENT_STATUS =
      LATEST_READINGS.field(SENSOR_READING_SECURED.STATUS);

  private final DSLContext dsl;

  public JooqMeasurementCsvExportQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void writeCsvUnlessTooManyRows(PagedRequest exportRequest, Writer writer)
      throws IOException {
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            exportRequest,
            MeasurementQueryRepository.MEASUREMENT_COLUMNS_BY_COL_ID,
            SENSOR_PARAMETER.SENSOR_ID.asc());

    var rows =
        dsl.select(
                EXPERIMENTS.NAME,
                DAS_KEY,
                SENSORS.NAME,
                SENSOR_PARAMETER.NAME,
                SENSOR_TYPES.NAME,
                NEWEST_MEASUREMENT,
                MEASURE_VALUE,
                MEASUREMENT_STATUS,
                SENSOR_PARAMETER.UNIT,
                SENSOR_PARAMETER.LOWER_ALARM_LIMIT,
                SENSOR_PARAMETER.UPPER_ALARM_LIMIT,
                SENSORS.ACTIVE,
                SENSORS.X,
                SENSORS.Y,
                SENSORS.Z,
                SENSORS.COMMENT)
            .from(MeasurementQueryRepository.JOINED_FROM)
            .where(criteria.condition())
            .orderBy(MeasurementQueryRepository.withStableTiebreaker(criteria.sortFields()))
            // one more than allowed, to tell a table that is too large from one that is not
            .limit(exportRequest.limit() + 1)
            .fetch();
    if (rows.size() > exportRequest.limit()) {
      throw new TooManyExportRowsException(exportRequest.limit());
    }

    CsvWriter.writeRow(writer, HEADER);
    for (Record r : rows) {
      CsvWriter.writeRow(writer, rowOf(r));
    }
  }

  private static List<Object> rowOf(Record r) {
    return Arrays.asList(
        r.get(EXPERIMENTS.NAME),
        r.get(DAS_KEY),
        r.get(SENSORS.NAME),
        r.get(SENSOR_PARAMETER.NAME),
        r.get(SENSOR_TYPES.NAME),
        r.get(NEWEST_MEASUREMENT),
        r.get(MEASURE_VALUE),
        MeasurementStatus.fromDbValue(r.get(MEASUREMENT_STATUS)),
        r.get(SENSOR_PARAMETER.UNIT),
        r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
        r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT),
        r.get(SENSORS.ACTIVE),
        r.get(SENSORS.X),
        r.get(SENSORS.Y),
        r.get(SENSORS.Z),
        r.get(SENSORS.COMMENT));
  }
}
