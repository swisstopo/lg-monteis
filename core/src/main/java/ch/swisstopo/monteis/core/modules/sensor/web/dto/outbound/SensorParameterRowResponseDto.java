package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import java.util.UUID;

/**
 * One row of the sensor grid's paged list, at (Sensor, SensorParameter) grain: a sensor with N
 * parameters yields N rows sharing the same sensor fields, and a sensor with none yields exactly
 * one row with {@code parameter} null (a {@code LEFT JOIN}, not dropped). Distinct from {@link
 * SensorResponseDto} (used by the single-sensor read/write endpoints), which nests the full
 * parameter list on one row per sensor.
 *
 * <p>{@code mainExperiment} is a minimal id/name reference rather than the full {@link
 * ExperimentResponseDto} - the grid only ever displays {@code mainExperiment.name}, and this
 * projection is read directly from a jOOQ join with no domain round-trip to hydrate the rest of
 * an experiment (period, status, sensorCount).
 */
public record SensorParameterRowResponseDto(
    UUID sensorId,
    String name,
    String dasSensorAlias,
    Das das,
    UUID fulcrumId,
    MainExperimentRefDto mainExperiment,
    CoordinatesDto coordinates,
    Boolean active,
    String comment,
    SensorParameterResponseDto parameter) {

  public record MainExperimentRefDto(UUID id, String name) {}
}
