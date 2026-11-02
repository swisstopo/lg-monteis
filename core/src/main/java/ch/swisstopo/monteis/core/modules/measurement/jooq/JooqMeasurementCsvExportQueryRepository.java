package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.MeasurementQueryRepository.DAS_KEY;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.MeasurementQueryRepository.LATEST_READINGS;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
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
 * Streams all measurement rows matching a filter/sort as CSV, one row at a time, without ever
 * materializing the full result set in memory. The header row, the jOOQ cursor iteration and every
 * row write all happen inside this single {@code @Transactional(readOnly = true)} method, so the DB
 * connection (and the RLS context Postgres enforces against it) stays open for the whole export.
 *
 * <p>Same row grain, join, filter/sort semantics and default sort as {@link
 * MeasurementQueryRepository#findPaged} (the grid's backing query). The display-only trend column
 * is left out.
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

  private final DSLContext dsl;

  public JooqMeasurementCsvExportQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException {
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            exportRequest,
            MeasurementQueryRepository.MEASUREMENT_COLUMNS_BY_COL_ID,
            SENSOR_PARAMETER.SENSOR_ID.asc());

    CsvWriter.writeRow(writer, HEADER);

    try (var cursor =
        dsl.select(
                EXPERIMENTS.NAME,
                DAS_KEY,
                SENSORS.NAME,
                SENSOR_PARAMETER.NAME,
                SENSOR_TYPES.NAME,
                NEWEST_MEASUREMENT,
                MEASURE_VALUE,
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
            .orderBy(criteria.sortFields())
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        CsvWriter.writeRow(
            writer,
            Arrays.asList(
                r.get(EXPERIMENTS.NAME),
                r.get(DAS_KEY),
                r.get(SENSORS.NAME),
                r.get(SENSOR_PARAMETER.NAME),
                r.get(SENSOR_TYPES.NAME),
                r.get(NEWEST_MEASUREMENT),
                r.get(MEASURE_VALUE),
                r.get(SENSOR_PARAMETER.UNIT),
                r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
                r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT),
                r.get(SENSORS.ACTIVE),
                r.get(SENSORS.X),
                r.get(SENSORS.Y),
                r.get(SENSORS.Z),
                r.get(SENSORS.COMMENT)));
        writer.flush();
      }
    }
  }
}
