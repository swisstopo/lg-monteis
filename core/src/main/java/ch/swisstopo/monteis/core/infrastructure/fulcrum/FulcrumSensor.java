package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * One row of the Fulcrum systems-and-sensors table, cut down to what Monteis reads.
 *
 * <p>Hand-written rather than generated, because the Query API returns whatever columns the
 * Fulcrum app happens to define and the spec types its response as an empty object. The raw row
 * carries roughly 120 columns, most of them Fulcrum bookkeeping (record keys, edit durations, GPS
 * accuracies, per-device capture info) or intermediate values of Fulcrum's own coordinate
 * calculations; the statement in {@link FulcrumService} asks for these columns only, so the rest
 * never reach the application. Widening this record means widening that column list with it.
 *
 * <p>Fulcrum leaves absent values as {@code null} rather than omitting them, and the meaning of a
 * column depends on the record's category (a {@code System}, {@code Packer}, {@code Interval} or
 * {@code Sensor} row fills different columns), so every component here is nullable.
 **/
public record FulcrumSensor(
    @JsonProperty("_record_id") UUID recordId,
    @JsonProperty("_title") String title,
    @JsonProperty("system_or_component_category_and_type") List<String> categoryAndType,

    // Final coordinates (LV95), as computed by Fulcrum
    @JsonProperty("fx_point_with_offset") Double xPointWithOffset,
    @JsonProperty("fy_point_with_offset") Double yPointWithOffset,
    @JsonProperty("fz_point_with_offset") Double zPointWithOffset) {

  /** Fulcrum's category value marking a record as a sensor rather than a system or a packer. */
  private static final String SENSOR_CATEGORY = "Sensor";

  /**
   * Whether this record describes a sensor. The table also holds the systems, packers and
   * intervals a sensor is mounted in, all sharing the same columns.
   */
  public boolean isSensor() {
    return categoryAndType != null && categoryAndType.contains(SENSOR_CATEGORY);
  }
}
