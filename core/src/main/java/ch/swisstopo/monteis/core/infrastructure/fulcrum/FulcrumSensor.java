package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the Fulcrum systems-and-sensors table, cleaned up for use inside MonTEIS.
 *
 * <p>Hand-written rather than generated, because the Query API returns whatever columns the
 * Fulcrum app happens to define and the spec types its response as an empty object. The raw row
 * carries roughly 120 columns, most of them Fulcrum bookkeeping (record keys, edit durations, GPS
 * accuracies, per-device capture info) or intermediate values of Fulcrum's own coordinate
 * calculations. This record maps the subset that describes a sensor and its installation;
 * everything else is dropped during deserialization.
 *
 * <p>Fulcrum leaves absent values as {@code null} rather than omitting them, and the meaning of a
 * column depends on the record's category (a {@code System}, {@code Packer}, {@code Interval} or
 * {@code Sensor} row fills different columns), so every component here is nullable.
 *
 * @param recordId Fulcrum's record id, matching {@code Sensor.fulcrumId} in MonTEIS
 * @param title Fulcrum's computed record title, identical to {@link #automaticName()}
 * @param categoryAndType category path of the record, e.g. {@code ["Sensor", "Point", "T"]} or
 *     {@code ["System", "MMMS"]} - the first element tells apart sensors from systems, packers
 *     and intervals
 * @param systemLink record ids of the system this record belongs to
 * @param sensorId identifier used by the data acquisition system (free text, not a UUID)
 * @param fdzPointOffset vertical offset between the sensor's cabinet/rack centerline and its
 *     actual measuring point
 */
@SuppressWarnings("java:S107") // A flat projection of a wide upstream table; grouping would only
// hide which Fulcrum column each value came from.
public record FulcrumSensor(
    // --- Fulcrum record metadata ---
    @JsonProperty("_record_id") UUID recordId,
    @JsonProperty("_title") String title,
    @JsonProperty("_status") String status,
    @JsonProperty("_version") Integer version,
    @JsonProperty("_created_at") Instant createdAt,
    @JsonProperty("_updated_at") Instant updatedAt,
    @JsonProperty("_latitude") Double latitude,
    @JsonProperty("_longitude") Double longitude,

    // --- Identity and classification ---
    @JsonProperty("system_or_component_category_and_type") List<String> categoryAndType,
    @JsonProperty("occurrence") String occurrence,
    @JsonProperty("system_link") List<UUID> systemLink,
    @JsonProperty("automatic_name") String automaticName,
    @JsonProperty("sensor_id") String sensorId,
    @JsonProperty("historic_name") String historicName,
    @JsonProperty("identifier") String identifier,
    @JsonProperty("monteis_id") String monteisId,
    @JsonProperty("monteis_sensor_names") String monteisSensorNames,

    // --- Experiment and borehole ---
    @JsonProperty("experiment") List<UUID> experiments,
    @JsonProperty("experiment_id") UUID experimentId,
    @JsonProperty("experiment_abbreviation") String experimentAbbreviation,
    @JsonProperty("related_to_borehole") List<UUID> relatedToBorehole,
    @JsonProperty("borehole_name") String boreholeName,
    @JsonProperty("borehole_bim_id") String boreholeBimId,
    @JsonProperty("installed_date") Instant installedDate,

    // --- Hardware ---
    @JsonProperty("system_name") String systemName,
    @JsonProperty("system_manufacturer") String systemManufacturer,
    @JsonProperty("manufacturer") String manufacturer,
    @JsonProperty("series") String series,
    @JsonProperty("model") String model,
    @JsonProperty("wellcad_code") String wellcadCode,
    @JsonProperty("comment_to_system") String commentToSystem,
    @JsonProperty("comment_feature") String commentFeature,

    // --- Installation ---
    @JsonProperty("sensor_inside_or_outside_borehole") String sensorInsideOrOutsideBorehole,
    @JsonProperty("borehole_packer_or_interval_state_recording") String stateRecording,
    @JsonProperty("select_borehole_packer_or_interval") List<UUID> packerOrInterval,
    @JsonProperty("connected_to_data_acquisition_system") List<UUID> dataAcquisitionSystem,
    @JsonProperty("bim_id_of_data_acquisition_system") String dataAcquisitionSystemBimId,
    @JsonProperty("located_in_cabinet_or_rack") List<UUID> cabinetOrRack,
    @JsonProperty("bim_id_of_cabinet_or_rack") String cabinetOrRackBimId,
    @JsonProperty("number") Integer number,
    @JsonProperty("start_distance_in_m") Double startDistanceInM,
    @JsonProperty("end_distance_in_m") Double endDistanceInM,
    @JsonProperty("calculated_length_in_m") Double calculatedLengthInM,
    @JsonProperty("radius_in_m") Double radiusInM,
    @JsonProperty("azimuth_position_in_degrees") Double azimuthPositionInDegrees,
    @JsonProperty("external_diameter_in_mm") Double externalDiameterInMm,
    @JsonProperty("reference_for_depth_calculation") String referenceForDepthCalculation,
    @JsonProperty("installed_depth_from_reference_in_m") Double installedDepthFromReferenceInM,
    @JsonProperty("reference_installed_at_angle_in_degrees") Double referenceAngleInDegrees,

    // --- Final coordinates (LV95), as computed by Fulcrum ---
    @JsonProperty("fx_centerline") Double xCenterline,
    @JsonProperty("fy_centerline") Double yCenterline,
    @JsonProperty("fz_centerline") Double zCenterline,
    @JsonProperty("fx_point_with_offset") Double xPointWithOffset,
    @JsonProperty("fy_point_with_offset") Double yPointWithOffset,
    @JsonProperty("fz_point_with_offset") Double zPointWithOffset,
    @JsonProperty("fdz_point_offset") Double fdzPointOffset) {

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
