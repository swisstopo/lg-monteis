package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import java.util.List;

/**
 * The owners of an experiment the caller gets to see. {@code unavailable} means Keycloak could
 * not be asked, so the owners are unknown right now, as opposed to an experiment that has none.
 * Build it with {@link #of}, {@link #NONE} or {@link #UNAVAILABLE}.
 */
public record VisibleOwners(List<DirectoryUser> users, boolean unavailable) {

  public static final VisibleOwners NONE = new VisibleOwners(List.of(), false);
  public static final VisibleOwners UNAVAILABLE = new VisibleOwners(List.of(), true);

  public VisibleOwners {
    users = List.copyOf(users);
  }

  public static VisibleOwners of(List<DirectoryUser> users) {
    return users.isEmpty() ? NONE : new VisibleOwners(users, false);
  }
}
