package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Sensor.JAVERS_TYPE)
public class Sensor implements Auditable {
  public static final String JAVERS_TYPE = "Sensor";

  @Id private UUID id;

  private String name;
  private String dasSensorAlias;
  private UUID fulcrumId;
  private Experiment mainExperiment;
  private Coordinates coordinates;
  private Boolean active;
  private String comment;
  private Integer version;
  private List<SensorParameter> parameters = new ArrayList<>();

  /**
   * Constructor for creating a NEW Sensor from a web request.
   * ID and Version are omitted as they are handled by the infrastructure layer.
   */
  @SuppressWarnings("java:S107")
  @Default
  public Sensor(
      String code,
      String name,
      SensorType type,
      Unit unit,
      String comment,
      Coordinates coordinates,
      AlarmLimits alarmLimits,
      Boolean active,
      Formula formula) {
    this.name = name;

    this.comment = comment;
    this.coordinates = coordinates;
    this.active = active;
  }

  /**
   * Constructor for REBUILDING an existing Sensor from the database (jOOQ).
   */
  @SuppressWarnings("java:S107")
  public Sensor(
      UUID id,
      String name,
      String dasSensorAlias,
      UUID fulcrumId,
      Experiment mainExperiment,
      Coordinates coordinates,
      Boolean active,
      String comment,
      Integer version,
      List<SensorParameter> parameters) {
    this.id = id;
    this.name = name;
    this.dasSensorAlias = dasSensorAlias;
    this.fulcrumId = fulcrumId;
    this.mainExperiment = mainExperiment;
    this.coordinates = coordinates;
    this.active = active;
    this.comment = comment;
    this.version = version;
    this.parameters = parameters;
  }

  // --- Getters and Setters ---

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

  public String getDasSensorAlias() {
    return dasSensorAlias;
  }

  public void setDasSensorAlias(String dasSensorAlias) {
    this.dasSensorAlias = dasSensorAlias;
  }

  public UUID getFulcrumId() {
    return fulcrumId;
  }

  public void setFulcrumId(UUID fulcrumId) {
    this.fulcrumId = fulcrumId;
  }

  public Experiment getMainExperiment() {
    return mainExperiment;
  }

  public void setMainExperiment(Experiment mainExperiment) {
    this.mainExperiment = mainExperiment;
  }

  public Coordinates getCoordinates() {
    return coordinates;
  }

  public void setCoordinates(Coordinates coordinates) {
    this.coordinates = coordinates;
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

  public List<SensorParameter> getParameters() {
    return parameters;
  }

  public void setParameters(List<SensorParameter> parameters) {
    this.parameters = parameters;
  }
}
