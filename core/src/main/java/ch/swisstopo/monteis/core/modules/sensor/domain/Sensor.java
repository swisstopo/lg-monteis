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
  private Bounds bounds;
  private Formula formula;
  private Integer version;

  /**
   * Constructor for creating a NEW Sensor from a web request.
   * ID and Version are omitted as they are handled by the infrastructure layer.
   */
  @Default
  public Sensor(String code, String name, Bounds bounds, Formula formula) {
    this.code = code;
    this.name = name;
    this.bounds = bounds;
    this.formula = formula != null ? formula : new Formula();
  }

  /**
   * Constructor for REBUILDING an existing Sensor from the database (jOOQ).
   */
  public Sensor(
      Long id, String code, String name, Bounds bounds, Formula formula, Integer version) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.bounds = bounds;
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

  public Bounds getBounds() {
    return bounds;
  }

  public void setBounds(Bounds bounds) {
    this.bounds = bounds;
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
