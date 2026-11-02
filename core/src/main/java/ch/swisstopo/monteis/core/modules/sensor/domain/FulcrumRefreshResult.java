package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.List;

/**
 * Outcome of a Fulcrum refresh.
 *
 * @param checked sensors compared with their Fulcrum record
 * @param updated of these, the sensors whose coordinates changed
 * @param skipped sensors that could not be compared or saved
 */
public record FulcrumRefreshResult(int checked, int updated, List<SkippedSensor> skipped) {}
