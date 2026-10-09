package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;

public sealed interface Pis {

  record Known(List<DirectoryUser> users) implements Pis {
    public Known {
      users = List.copyOf(users);
    }
  }

  record NoWriteGroup() implements Pis {}

  record AccessDenied(String reason) implements Pis {}

  record KeycloakUnavailable(String reason) implements Pis {}
}
