package ch.swisstopo.monteis.core.modules.overview.domain;

/**
 * How a single reading's normalised value relates to its sensor parameter's configured limits.
 * Drives the colour a client renders the value in; the ordering below is the precedence, an alarm
 * outranks a range violation.
 */
public enum MeasurementState {
  /** Outside the sensor parameter's alarm limits. */
  ALARM,
  /** Inside the alarm limits, but outside the sensor's measurement range (see range_category). */
  EXCEEDS_RANGE,
  /** Within both. */
  OK;

  /** The range_category value meaning "inside the measurement range"; anything else exceeds it. */
  private static final String RANGE_STATUS_CORRECT = "correct";

  /**
   * @param normValue the reading's normalized value, null when the pipeline could not compute one
   * @param lowerAlarmLimit sensor parameter's lower alarm limit
   * @param upperAlarmLimit sensor parameter's upper alarm limit
   * @param rangeStatus the reading's range_category, as stored in TimescaleDB
   */
  public static MeasurementState of(
      Double normValue, Double lowerAlarmLimit, Double upperAlarmLimit, String rangeStatus) {
    if (normValue != null
        && lowerAlarmLimit != null
        && upperAlarmLimit != null
        && (normValue < lowerAlarmLimit || normValue > upperAlarmLimit)) {
      return ALARM;
    }
    if (rangeStatus != null && !RANGE_STATUS_CORRECT.equals(rangeStatus)) {
      return EXCEEDS_RANGE;
    }
    return OK;
  }
}
