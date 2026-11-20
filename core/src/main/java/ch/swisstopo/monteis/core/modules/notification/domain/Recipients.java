package ch.swisstopo.monteis.core.modules.notification.domain;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record Recipients(List<RecipientCandidate> candidates, boolean saved) {

  public Recipients {
    candidates = List.copyOf(candidates);
  }

  public static Recipients pisUntilSaved(ExperimentMembers members) {
    List<UUID> piIds = members.pis().stream().map(DirectoryUser::id).toList();
    return new Recipients(candidatesSelecting(members, piIds), false);
  }

  public static Recipients savedAmong(ExperimentMembers members, Collection<UUID> savedIds) {
    return new Recipients(candidatesSelecting(members, savedIds), true);
  }

  private static List<RecipientCandidate> candidatesSelecting(
      ExperimentMembers members, Collection<UUID> selectedIds) {
    return members.recipientCandidates().stream()
        .map(user -> new RecipientCandidate(user, selectedIds.contains(user.id())))
        .toList();
  }
}
