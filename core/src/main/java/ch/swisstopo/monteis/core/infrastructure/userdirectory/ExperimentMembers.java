package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record ExperimentMembers(List<DirectoryUser> all, List<DirectoryUser> pis) {

  public ExperimentMembers {
    all = List.copyOf(all);
    pis = List.copyOf(pis);
  }

  // group members only: admins and global editors get access by role, not group (MON-245 T3)
  public List<DirectoryUser> recipientCandidates() {
    return all;
  }

  public List<DirectoryUser> among(Collection<UUID> userIds) {
    return all.stream().filter(user -> userIds.contains(user.id())).toList();
  }
}
