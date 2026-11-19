package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record ExperimentMembers(List<DirectoryUser> pis, List<DirectoryUser> withAccess) {

  public ExperimentMembers {
    pis = List.copyOf(pis);
    withAccess = List.copyOf(withAccess);
  }

  public List<DirectoryUser> withAccessAmong(Collection<UUID> userIds) {
    return withAccess.stream().filter(user -> userIds.contains(user.id())).toList();
  }
}
