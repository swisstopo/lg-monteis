package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Sensor.JAVERS_TYPE)
public class Sensor implements Auditable {
  public static final String JAVERS_TYPE = "Sensor";

  @Id private Long id;
  private String code;
  private String name;
  private SensorType type;
  private Unit unit;
  private String comment;
  private Coordinates coordinates;
  private AlarmBounds alarmBounds;
  private Boolean active;
  private Formula formula;
  private Integer version;

  /**
   * Constructor for creating a NEW Sensor from a web request.
   * ID and Version are omitted as they are handled by the infrastructure layer.
   */
  @Default
  public Sensor(
      String code,
      String name,
      SensorType type,
      Unit unit,
      String comment,
      Coordinates coordinates,
      AlarmBounds alarmBounds,
      Boolean active,
      Formula formula) {
    this.code = code;
    this.name = name;
    this.type = type;
    this.unit = unit;
    this.comment = comment;
    this.coordinates = coordinates;
    this.alarmBounds = alarmBounds;
    this.active = active;
    this.formula = formula != null ? formula : new Formula();
  }

  /**
   * Constructor for REBUILDING an existing Sensor from the database (jOOQ).
   */
  public Sensor(
      Long id,
      String code,
      String name,
      SensorType type,
      Unit unit,
      String comment,
      Coordinates coordinates,
      AlarmBounds alarmBounds,
      Boolean active,
      Formula formula,
      Integer version) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.unit = unit;
    this.type = type;
    this.comment = comment;
    this.coordinates = coordinates;
    this.alarmBounds = alarmBounds;
    this.active = active;
    this.formula = formula;
    this.version = version;
  }

  // --- Getters and Setters ---

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Coordinates getCoordinates() {
    return coordinates;
  }

  public void setCoordinates(Coordinates coordinates) {
    this.coordinates = coordinates;
  }

  public AlarmBounds getAlarmBounds() {
    return alarmBounds;
  }

  public void setAlarmBounds(AlarmBounds alarmBounds) {
    this.alarmBounds = alarmBounds;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Formula getFormula() {
    return formula;
  }

  public void setFormula(Formula formula) {
    this.formula = formula;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
