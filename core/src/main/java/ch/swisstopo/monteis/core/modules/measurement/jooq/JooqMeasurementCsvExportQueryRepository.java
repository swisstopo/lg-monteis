package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.MeasurementQueryRepository.DAS_KEY;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.MeasurementQueryRepository.LATEST_READINGS;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.exception.TooManyExportRowsException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes all measurement rows matching a filter/sort as CSV, the way the measurements table shows
 * them: its column headers and its cell formatting (webapp measurements-table/columns.ts), plus the
 * measurement status the table only shows as a cell color. The display-only trend column is left
 * out.
 *
 * <p>Same row grain, join, filter/sort semantics and default sort as {@link
 * MeasurementQueryRepository#findPaged} (the grid's backing query). The rows are fetched first,
 * capped at the export limit (an export matching more rows is refused with {@link
 * TooManyExportRowsException}), and only then written, so neither the transaction nor the DB
 * connection is held while the client reads, and a failing query still answers with an error
 * instead of a cut off file.
 */
@Repository
@Transactional(readOnly = true)
public class JooqMeasurementCsvExportQueryRepository
    implements MeasurementCsvExportQueryRepository {

  // the English labels of the table's columns (webapp/public/i18n/en.json), the only language
  private static final List<String> HEADER =
      List.of(
          "Experiment",
          "DAS Key",
          "Sensor Name",
          "Parameter",
          "Sensor Type",
          "Newest Measurement",
          "Value",
          "Measurement Status",
          "Unit",
          "Alarm Limit From",
          "Alarm Limit To",
          "Active",
          "X (Local)",
          "Y (Local)",
          "Z (Local)",
          "Comment");

  // sensor.unit.option.*.symbol in webapp/public/i18n/en.json
  private static final Map<Unit, String> UNIT_SYMBOLS =
      Map.of(
          Unit.SECONDS, "s",
          Unit.METER, "m",
          Unit.KILOGRAM, "kg",
          Unit.AMPERE, "A",
          Unit.KELVIN, "K",
          Unit.MOLE, "mol",
          Unit.CANDELA, "cd");

  private static final Map<MeasurementStatus, String> STATUS_LABELS =
      Map.of(
          MeasurementStatus.CORRECT, "Correct",
          MeasurementStatus.TOO_LOW, "Too low",
          MeasurementStatus.TOO_HIGH, "Too high");

  // the table's date pipe 'medium' in its en-GB locale, in the Swiss time zone the site is in
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.UK)
          .withZone(ZoneId.of("Europe/Zurich"));

  private static final String NO_VALUE = "-";

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
  public void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException {
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
            .orderBy(criteria.sortFields())
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
    OffsetDateTime newestMeasurement = r.get(NEWEST_MEASUREMENT);
    Double measureValue = r.get(MEASURE_VALUE);
    MeasurementStatus status = MeasurementStatus.fromDbValue(r.get(MEASUREMENT_STATUS));
    var unit = r.get(SENSOR_PARAMETER.UNIT);
    return Arrays.asList(
        r.get(EXPERIMENTS.NAME),
        r.get(DAS_KEY),
        r.get(SENSORS.NAME),
        r.get(SENSOR_PARAMETER.NAME),
        r.get(SENSOR_TYPES.NAME),
        newestMeasurement != null ? TIMESTAMP_FORMAT.format(newestMeasurement) : NO_VALUE,
        measureValue != null
            ? BigDecimal.valueOf(measureValue).setScale(2, RoundingMode.HALF_UP).toPlainString()
            : NO_VALUE,
        status != null ? STATUS_LABELS.get(status) : null,
        unit != null ? UNIT_SYMBOLS.get(Unit.valueOf(unit.name())) : null,
        number(r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT)),
        number(r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT)),
        Boolean.TRUE.equals(r.get(SENSORS.ACTIVE)) ? "Yes" : "No",
        number(r.get(SENSORS.X)),
        number(r.get(SENSORS.Y)),
        number(r.get(SENSORS.Z)),
        r.get(SENSORS.COMMENT));
  }

  // the table shows these unformatted, as JavaScript prints a number: 100 rather than 100.0
  private static String number(Double value) {
    return value != null ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : null;
  }
}
