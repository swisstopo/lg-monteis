package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DirectoryUsers {

  public static final String NOT_ELIGIBLE_KEY = "experiment.owner.not-eligible";

  private DirectoryUsers() {}

  public static void rejectUsersOutside(Set<UUID> ids, List<DirectoryUser> allowed, String field) {
    Set<UUID> outside = idsOutside(ids, allowed);
    if (!outside.isEmpty()) {
      throw new FieldBusinessValidationException(field, outside, NOT_ELIGIBLE_KEY, Map.of());
    }
  }

  public static Set<UUID> idsOutside(Collection<UUID> ids, List<DirectoryUser> users) {
    Set<UUID> userIds = users.stream().map(DirectoryUser::id).collect(Collectors.toSet());
    return ids.stream().filter(id -> !userIds.contains(id)).collect(Collectors.toUnmodifiableSet());
  }
}
