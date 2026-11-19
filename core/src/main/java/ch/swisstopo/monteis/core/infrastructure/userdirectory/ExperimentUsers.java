package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record ExperimentUsers(List<DirectoryUser> all, List<DirectoryUser> pis) {

  public ExperimentUsers {
    all = List.copyOf(all);
    pis = List.copyOf(pis);
  }

  public List<DirectoryUser> among(Collection<UUID> userIds) {
    return all.stream().filter(user -> userIds.contains(user.id())).toList();
  }
}
