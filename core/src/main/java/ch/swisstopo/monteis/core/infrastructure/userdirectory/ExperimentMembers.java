package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ExperimentMembers(
    List<DirectoryUser> all, List<DirectoryUser> pis, boolean hasWriteGroup) {

  public ExperimentMembers {
    all = List.copyOf(all);
    pis = List.copyOf(pis);
  }

  // group members only: admins and global editors get access by role, not group (MON-245 T3)
  public List<DirectoryUser> recipientCandidates() {
    return all;
  }

  public Set<UUID> nonMembersAmong(Collection<UUID> userIds) {
    Set<UUID> memberIds = all.stream().map(DirectoryUser::id).collect(Collectors.toSet());
    return userIds.stream()
        .filter(userId -> !memberIds.contains(userId))
        .collect(Collectors.toUnmodifiableSet());
  }
}
