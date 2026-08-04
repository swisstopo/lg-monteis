package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Experiment.JAVERS_TYPE)
public class Experiment implements Auditable {
  public static final String JAVERS_TYPE = "Experiment";

  @Id private Long id;
  private String name;
  private String owner;
  private ExperimentDates experimentDates;
  private String description;
  private Status status;
  private Integer version;

  /**
   * Constructor for creating a NEW Sensor from a web request.
   * ID and Version are omitted as they are handled by the infrastructure layer.
   */
  @SuppressWarnings("java:S107")
  @Default
  public Experiment(
      String name,
      String owner,
      ExperimentDates experimentDates,
      String description,
      Status status) {
    this.name = name;
    this.owner = owner;
    this.experimentDates = experimentDates;
    this.description = description;
    this.status = status;
  }

  /**
   * Constructor for REBUILDING an existing Sensor from the database (jOOQ).
   */
  @SuppressWarnings("java:S107")
  public Experiment(
      Long id,
      String name,
      String owner,
      ExperimentDates experimentDates,
      String description,
      Status status,
      Integer version) {
    this.id = id;
    this.name = name;
    this.owner = owner;
    this.experimentDates = experimentDates;
    this.description = description;
    this.status = status;
    this.version = version;
  }

  // --- Getters and Setters ---

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public ExperimentDates getExperimentDates() {
    return experimentDates;
  }

  public void setExperimentDates(ExperimentDates experimentDates) {
    this.experimentDates = experimentDates;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
