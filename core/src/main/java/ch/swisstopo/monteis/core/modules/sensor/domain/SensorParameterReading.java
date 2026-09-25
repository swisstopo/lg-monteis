package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.time.Instant;

public class SensorParameterReading {
  private String parameter;
  private Double value;
  private Double rawValue;
  private Integer statusCode;
  private String status;
  private Instant timestamp;

  public SensorParameterReading(
      String parameter,
      Double value,
      Double rawValue,
      Integer statusCode,
      String status,
      Instant timestamp) {
    this.parameter = parameter;
    this.value = value;
    this.rawValue = rawValue;
    this.statusCode = statusCode;
    this.status = status;
    this.timestamp = timestamp;
  }

  public String getParameter() {
    return parameter;
  }

  public void setParameter(String parameter) {
    this.parameter = parameter;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public Double getRawValue() {
    return rawValue;
  }

  public void setRawValue(Double rawValue) {
    this.rawValue = rawValue;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
