package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;

public record ExperimentMembers(
    List<DirectoryUser> all, List<DirectoryUser> pis, boolean hasWriteGroup) {

  public ExperimentMembers {
    all = List.copyOf(all);
    pis = List.copyOf(pis);
  }
}
