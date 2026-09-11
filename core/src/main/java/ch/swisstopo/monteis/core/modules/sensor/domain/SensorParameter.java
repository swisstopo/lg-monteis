package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.Objects;
import java.util.UUID;
import org.javers.core.metamodel.annotation.Id;

public class SensorParameter {
  @Id private UUID id;
  private String name;
  private String dasParameterAlias;
  private SensorType type;
  private Unit unit;
  private Formula formula;
  private AlarmLimits alarmLimits;
  private Boolean active;
  private String comment;
  private Integer version;

  public SensorParameter(
      UUID id,
      String name,
      String dasParameterAlias,
      SensorType type,
      Unit unit,
      Formula formula,
      AlarmLimits alarmLimits,
      Boolean active,
      String comment,
      Integer version) {
    this.id = id;
    this.name = name;
    this.dasParameterAlias = dasParameterAlias;
    this.type = type;
    this.unit = unit;
    this.formula = formula;
    this.alarmLimits = alarmLimits;
    this.active = active;
    this.comment = comment;
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

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
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
