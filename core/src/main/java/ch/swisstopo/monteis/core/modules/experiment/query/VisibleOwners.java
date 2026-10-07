package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import java.util.List;

/**
 * The owners of an experiment the caller gets to see, and whether that is all of them. Build it
 * with {@link #of} or one of the constants.
 */
public record VisibleOwners(List<DirectoryUser> users, OwnersStatus status) {

  public static final VisibleOwners NONE = new VisibleOwners(List.of(), OwnersStatus.SHOWN);
  public static final VisibleOwners KEYCLOAK_UNAVAILABLE =
      new VisibleOwners(List.of(), OwnersStatus.KEYCLOAK_UNAVAILABLE);
  public static final VisibleOwners ACCESS_DENIED =
      new VisibleOwners(List.of(), OwnersStatus.ACCESS_DENIED);
  public static final VisibleOwners NO_WRITE_GROUP =
      new VisibleOwners(List.of(), OwnersStatus.NO_WRITE_GROUP);

  public VisibleOwners {
    users = List.copyOf(users);
  }

  public static VisibleOwners of(List<DirectoryUser> users) {
    return users.isEmpty() ? NONE : new VisibleOwners(users, OwnersStatus.SHOWN);
  }
}
