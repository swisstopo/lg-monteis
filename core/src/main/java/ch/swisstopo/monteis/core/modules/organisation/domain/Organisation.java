package ch.swisstopo.monteis.core.modules.organisation.domain;

import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import java.util.UUID;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(Organisation.JAVERS_TYPE)
public class Organisation {
  public static final String JAVERS_TYPE = "Organisation";

  @Id private UUID id;
  private String name;
  private String comment;

  /**
   * Constructor for creating a NEW Organisation from a web request.
   */
  @Default
  public Organisation(String name, String comment) {
    this.name = name;
    this.comment = comment;
  }

  /**
   * Constructor for REBUILDING an existing Organisation from the database (jOOQ).
   */
  public Organisation(UUID id, String name, String comment) {
    this.id = id;
    this.name = name;
    this.comment = comment;
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

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }
}
