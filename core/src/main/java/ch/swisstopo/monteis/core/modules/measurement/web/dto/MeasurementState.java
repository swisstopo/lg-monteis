package ch.swisstopo.monteis.core.modules.measurement.web.dto;

/**
 * How a single reading's normalised value relates to its sensor parameter's configured limits.
 * Drives the colour a client renders the value in; the ordering below is the precedence, an alarm
 * outranks a range violation.
 */
public enum MeasurementState {
  /** Outside the sensor parameter's alarm limits. */
  ALARM,

  /** Inside the alarm limits, but outside the sensor's measurement range. */
  EXCEEDS_RANGE,

  /** Within both. */
  OK
}
