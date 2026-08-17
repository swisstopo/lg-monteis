package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import java.time.LocalDate;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Experiment.JAVERS_TYPE)
public class Experiment implements Auditable {
  public static final String JAVERS_TYPE = "Experiment";

  @Id private Long id;
  private String name;
  private String owner;
  private Status status;
  private Period period;
  private String comment;
  private Integer version;
  private Integer sensorCount;

  /**
   * Constructor for creating a NEW Experiment from a web request.
   */
  @SuppressWarnings("java:S107")
  @Default
  public Experiment(String name, String owner, Period period, String comment) {
    this.name = name;
    this.owner = owner;
    this.period = period;
    this.comment = comment;
  }

  /**
   * Constructor for REBUILDING an existing Experiment from the database (jOOQ).
   */
  @SuppressWarnings("java:S107")
  public Experiment(
      Long id, String name, Period period, String comment, Integer version, Integer sensorCount) {
    this.id = id;
    this.name = name;
    this.period = period;
    this.comment = comment;
    this.version = version;
    this.sensorCount = sensorCount;
  }

  public void calculateAndSetStatus(LocalDate today) {
    if (period == null || period.start() == null || period.end() == null) {
      return;
    }

    if (period.end().isBefore(today)) {
      this.status = Status.HISTORIC;
    } else if (period.start().isAfter(today)) {
      this.status = Status.UPCOMING;
    } else {
      this.status = Status.ACTIVE;
    }
  }

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

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
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
}
