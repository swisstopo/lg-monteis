package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import java.util.Objects;
import java.util.UUID;
import org.javers.core.metamodel.annotation.Id;

public class SensorParameter {
  private static final String DEFAULT_DAS_PARAMETER_ALIAS = "value";

  @Id private UUID id;
  private String name;
  private String dasParameterAlias;
  private SensorType type;
  private Unit unit;
  private Formula formula;
  private AlarmLimits alarmLimits;
  private RangeLimits rangeLimits;
  private boolean notificationActive;
  private Integer timeout;
  private Integer notificationTriggerCount;
  private boolean closelyMonitored;
  private AlarmLimits specialAlarmLimits;
  private Boolean active;
  private Integer version;

  /**
   * Constructor for creating a NEW SensorParameter from a web request.
   * ID and Version are omitted as they are handled by the infrastructure layer.
   */
  @SuppressWarnings("java:S107")
  @Default
  public SensorParameter(
      String name,
      String dasParameterAlias,
      SensorType type,
      Unit unit,
      Formula formula,
      AlarmLimits alarmLimits,
      RangeLimits rangeLimits,
      boolean notificationActive,
      Integer timeout,
      Integer notificationTriggerCount,
      boolean closelyMonitored,
      AlarmLimits specialAlarmLimits,
      Boolean active) {
    this.name = name;
    // Most DAS devices only ever report a single measurement, conventionally named "value"
    this.dasParameterAlias =
        (dasParameterAlias == null || dasParameterAlias.isBlank())
            ? DEFAULT_DAS_PARAMETER_ALIAS
            : dasParameterAlias;
    this.type = type;
    this.unit = unit;
    this.formula = formula;
    this.alarmLimits = alarmLimits;
    this.rangeLimits = rangeLimits;
    this.notificationActive = notificationActive;
    this.timeout = timeout;
    this.notificationTriggerCount = notificationTriggerCount;
    this.closelyMonitored = closelyMonitored;
    this.specialAlarmLimits = specialAlarmLimits;
    this.active = active;
  }

  @SuppressWarnings("java:S107")
  public SensorParameter(
      UUID id,
      String name,
      String dasParameterAlias,
      SensorType type,
      Unit unit,
      Formula formula,
      AlarmLimits alarmLimits,
      RangeLimits rangeLimits,
      boolean notificationActive,
      Integer timeout,
      Integer notificationTriggerCount,
      boolean closelyMonitored,
      AlarmLimits specialAlarmLimits,
      Boolean active,
      Integer version) {
    this.id = id;
    this.name = name;
    this.dasParameterAlias = dasParameterAlias;
    this.type = type;
    this.unit = unit;
    this.formula = formula;
    this.alarmLimits = alarmLimits;
    this.rangeLimits = rangeLimits;
    this.notificationActive = notificationActive;
    this.timeout = timeout;
    this.notificationTriggerCount = notificationTriggerCount;
    this.closelyMonitored = closelyMonitored;
    this.specialAlarmLimits = specialAlarmLimits;
    this.active = active;
    this.version = version;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDasParameterAlias() {
    return dasParameterAlias;
  }

  public void setDasParameterAlias(String dasParameterAlias) {
    this.dasParameterAlias = dasParameterAlias;
  }

  public SensorType getType() {
    return type;
  }

  public void setType(SensorType type) {
    this.type = type;
  }

  public Unit getUnit() {
    return unit;
  }

  public void setUnit(Unit unit) {
    this.unit = unit;
  }

  public Formula getFormula() {
    return formula;
  }

  public void setFormula(Formula formula) {
    this.formula = formula;
  }

  public AlarmLimits getAlarmLimits() {
    return alarmLimits;
  }

  public void setAlarmLimits(AlarmLimits alarmLimits) {
    this.alarmLimits = alarmLimits;
  }

  public RangeLimits getRangeLimits() {
    return rangeLimits;
  }

  public void setRangeLimits(RangeLimits rangeLimits) {
    this.rangeLimits = rangeLimits;
  }

  public boolean getNotificationActive() {
    return notificationActive;
  }

  public void setNotificationActive(boolean notificationActive) {
    this.notificationActive = notificationActive;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  public Integer getNotificationTriggerCount() {
    return notificationTriggerCount;
  }

  public void setNotificationTriggerCount(Integer notificationTriggerCount) {
    this.notificationTriggerCount = notificationTriggerCount;
  }

  public boolean getCloselyMonitored() {
    return closelyMonitored;
  }

  public void setCloselyMonitored(boolean closelyMonitored) {
    this.closelyMonitored = closelyMonitored;
  }

  public AlarmLimits getSpecialAlarmLimits() {
    return specialAlarmLimits;
  }

  public void setSpecialAlarmLimits(AlarmLimits specialAlarmLimits) {
    this.specialAlarmLimits = specialAlarmLimits;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public boolean changeTriggersPublish(SensorParameter old) {
    return old == null
        || !Objects.equals(old.getFormula().getExpression(), this.formula.getExpression())
        || !Objects.equals(old.getAlarmLimits(), this.alarmLimits);
  }
}
