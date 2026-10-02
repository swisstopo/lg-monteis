package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
record KeycloakUser(UUID id, String firstName, String lastName, String email, boolean enabled) {

  DirectoryUser toDirectoryUser() {
    return new DirectoryUser(id, firstName, lastName, email);
  }
}
