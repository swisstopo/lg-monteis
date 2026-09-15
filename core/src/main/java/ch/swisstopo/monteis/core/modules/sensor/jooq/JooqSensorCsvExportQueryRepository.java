package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorCsvExportQueryRepository;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams all (Sensor, SensorParameter) rows matching a filter/sort as CSV, one row at a time,
 * without ever materializing the full result set in memory: the header row, the jOOQ cursor
 * iteration and every row write all happen inside this single
 * {@code @Transactional(readOnly = true)} method, so the DB connection (and the RLS context
 * Postgres enforces against it) stays open for the whole export. This intentionally isn't a
 * {@code StreamingResponseBody} callback - that runs on a separate thread after the controller
 * method (and thus this transaction) has already returned/committed.
 *
 * <p>Same row grain, join, filter/sort semantics and default sort as {@link
 * JooqSensorParameterRowQueryRepository} (the grid's backing query) - a sensor with zero
 * parameters still yields exactly one row (parameter columns blank), a sensor with N parameters
 * yields N rows.
 */
@Repository
public class JooqSensorCsvExportQueryRepository implements SensorCsvExportQueryRepository {

  private static final List<String> HEADER =
      List.of(
          "dasSensorAlias",
          "name",
          "das",
          "fulcrumId",
          "mainExperiment.name",
          "coordinates.x",
          "coordinates.y",
          "coordinates.z",
          "active",
          "comment",
          "parameter.name",
          "parameter.dasParameterAlias",
          "parameter.type.name",
          "parameter.unit",
          "parameter.formula.expression",
          "parameter.alarmLimits.lower",
          "parameter.alarmLimits.upper",
          "parameter.active",
          "parameter.comment");

  private final DSLContext dsl;

  public JooqSensorCsvExportQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true)
  public void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException {
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            exportRequest,
            JooqSensorParameterRowQueryRepository.COLUMNS_BY_COL_ID,
            SENSORS.NAME.asc());

    CsvWriter.writeRow(writer, HEADER);

    try (var cursor =
        dsl.select(
                SENSORS.DAS_SENSOR_ALIAS,
                SENSORS.NAME,
                SENSORS.DAS,
                SENSORS.FULCRUM_ID,
                EXPERIMENTS.NAME,
                SENSORS.X,
                SENSORS.Y,
                SENSORS.Z,
                SENSORS.ACTIVE,
                SENSORS.COMMENT,
                SENSOR_PARAMETER.NAME,
                SENSOR_PARAMETER.DAS_PARAMETER_ALIAS,
                SENSOR_TYPES.NAME,
                SENSOR_PARAMETER.UNIT,
                FORMULAS.EXPRESSION,
                SENSOR_PARAMETER.LOWER_ALARM_LIMIT,
                SENSOR_PARAMETER.UPPER_ALARM_LIMIT,
                SENSOR_PARAMETER.ACTIVE,
                SENSOR_PARAMETER.COMMENT)
            .from(JooqSensorParameterRowQueryRepository.joinedFrom())
            .where(criteria.condition())
            .orderBy(
                JooqSensorParameterRowQueryRepository.withStableTiebreaker(criteria.sortFields()))
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        CsvWriter.writeRow(
            writer,
            Arrays.asList(
                r.get(SENSORS.DAS_SENSOR_ALIAS),
                r.get(SENSORS.NAME),
                r.get(SENSORS.DAS),
                r.get(SENSORS.FULCRUM_ID),
                r.get(EXPERIMENTS.NAME),
                r.get(SENSORS.X),
                r.get(SENSORS.Y),
                r.get(SENSORS.Z),
                r.get(SENSORS.ACTIVE),
                r.get(SENSORS.COMMENT),
                r.get(SENSOR_PARAMETER.NAME),
                r.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
                r.get(SENSOR_TYPES.NAME),
                r.get(SENSOR_PARAMETER.UNIT),
                r.get(FORMULAS.EXPRESSION),
                r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
                r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT),
                r.get(SENSOR_PARAMETER.ACTIVE),
                r.get(SENSOR_PARAMETER.COMMENT)));
        writer.flush();
      }
    }
  }
}
