package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.impl.DSL.lateral;
import static org.jooq.impl.DSL.select;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

public final class LatestReadingProjections {

  // Correlated per sensor_parameter, joined once by a query and reused by its projection and its
  // sort/filter map so "dasKey" resolves against the exact same lateral alias either way. Reads the
  // secured view, so row-level security applies.
  public static final Table<?> LATEST_READINGS =
      lateral(
              select(
                      SENSOR_READING_SECURED.TIMESTAMP,
                      SENSOR_READING_SECURED.NORM_VALUE,
                      SENSOR_READING_SECURED.RAW_VALUE,
                      SENSOR_READING_SECURED.DAS_KEY,
                      SENSOR_READING_SECURED.STATUS)
                  .from(SENSOR_READING_SECURED)
                  .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(SENSOR_PARAMETER.ID))
                  .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
                  .limit(1))
          .as("latest_readings");

  // Prefers the das_key actually stored on the latest reading - readings keep the key that was
  // valid when they were ingested even if the sensor/parameter alias is later remapped (see
  // db/meta/schema/V14) - falling back to composing it from current metadata (mirrors
  // ch.swisstopo.monteis.contracts.DasKey.compose) for a parameter with no readings yet.
  public static final Field<String> DAS_KEY =
      DSL.coalesce(
              LATEST_READINGS.field(SENSOR_READING_SECURED.DAS_KEY),
              DSL.concat(
                  SENSORS.DAS,
                  DSL.inline("__"),
                  SENSORS.DAS_SENSOR_ALIAS,
                  DSL.inline("__"),
                  SENSOR_PARAMETER.DAS_PARAMETER_ALIAS))
          .as("das_key");

  private LatestReadingProjections() {}
}
