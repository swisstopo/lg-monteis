package ch.swisstopo.monteis.core.modules.measurement.domain;

public enum MeasurementStatus {
  TOO_LOW("too_low"),
  CORRECT("correct"),
  TOO_HIGH("too_high");

  private final String dbValue;

  MeasurementStatus(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  public static MeasurementStatus fromDbValue(String dbValue) {
    if (dbValue == null) {
      return null;
    }

    for (MeasurementStatus state : values()) {
      if (state.dbValue.equals(dbValue)) {
        return state;
      }
    }

    throw new IllegalArgumentException("Unknown MeasurementStatus db value: " + dbValue);
  }
}
