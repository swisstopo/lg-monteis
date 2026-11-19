package ch.swisstopo.monteis.core.modules.notification.domain;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record Recipients(List<DirectoryUser> users, boolean saved) {

  public Recipients {
    users = List.copyOf(users);
  }

  public static Recipients pisUntilSaved(ExperimentMembers members) {
    return new Recipients(members.pis(), false);
  }

  public static Recipients savedAmong(ExperimentMembers members, Collection<UUID> savedIds) {
    return new Recipients(members.among(savedIds), true);
  }
}
