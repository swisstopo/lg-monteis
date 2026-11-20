package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;
import java.util.stream.Stream;

public record ExperimentMembers(List<DirectoryUser> pis, List<DirectoryUser> nonPis) {

  public ExperimentMembers {
    pis = List.copyOf(pis);
    nonPis = List.copyOf(nonPis);
  }

  public List<DirectoryUser> all() {
    return Stream.concat(pis.stream(), nonPis.stream()).sorted(DirectoryUser.BY_NAME).toList();
  }
}
