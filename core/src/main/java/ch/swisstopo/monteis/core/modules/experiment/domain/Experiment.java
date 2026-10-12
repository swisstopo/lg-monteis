package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.javers.core.metamodel.annotation.DiffIgnore;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Experiment.JAVERS_TYPE)
public class Experiment implements Auditable {
  public static final String JAVERS_TYPE = "Experiment";

  @Id private UUID id;
  private String name;
  private Period period;
  private String comment;
  private Integer version;
  // derived from the sensors, not something anyone changes on the experiment
  @DiffIgnore private Integer sensorCount;
  private Set<UUID> ownerIds = Set.of();
  private List<UUID> organisationIds = List.of();

  /**
   * Constructor for creating a NEW Experiment from a web request.
   */
  @Default
  public Experiment(String name, Period period, String comment) {
    this.name = name;
    this.period = period;
    this.comment = comment;
  }

  /**
   * Constructor for REBUILDING an existing Experiment from the database (jOOQ).
   */
  public Experiment(
      UUID id,
      String name,
      Period period,
      String comment,
      Integer version,
      Integer sensorCount,
      Set<UUID> ownerIds) {
    this.id = id;
    this.name = name;
    this.period = period;
    this.comment = comment;
    this.version = version;
    this.sensorCount = sensorCount;
    setOwnerIds(ownerIds);
  }

  public Status getStatus(LocalDate today) {
    return period.getStatus(today);
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

  public Period getPeriod() {
    return period;
  }

  public void setPeriod(Period period) {
    this.period = period;
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

  public Integer getSensorCount() {
    return sensorCount;
  }

  public void setSensorCount(Integer sensorCount) {
    this.sensorCount = sensorCount;
  }

  public Set<UUID> getOwnerIds() {
    return ownerIds;
  }

  public void setOwnerIds(Set<UUID> ownerIds) {
    this.ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
  }

  public List<UUID> getOrganisationIds() {
    return organisationIds;
  }

  public void setOrganisationIds(List<UUID> organisationIds) {
    this.organisationIds =
        List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(organisationIds)));
  }
}
