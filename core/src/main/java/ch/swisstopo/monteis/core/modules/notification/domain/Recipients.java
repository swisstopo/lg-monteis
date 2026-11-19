package ch.swisstopo.monteis.core.modules.notification.domain;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import java.util.List;

public record Recipients(List<DirectoryUser> users, boolean saved) {

  public Recipients {
    users = List.copyOf(users);
  }
}
