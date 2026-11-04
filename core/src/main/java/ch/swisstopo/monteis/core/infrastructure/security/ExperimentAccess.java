package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ExperimentAccess(
    boolean isAdmin, Set<UUID> readableExperimentIds, Set<UUID> writableExperimentIds) {

  public static final ExperimentAccess NONE = new ExperimentAccess(false, Set.of(), Set.of());

  private static final ExperimentAccess ADMIN = new ExperimentAccess(true, Set.of(), Set.of());

  static ExperimentAccess of(
      Set<String> permissions,
      Collection<UUID> readExperimentIds,
      Collection<UUID> writeExperimentIds) {
    if (permissions.contains(Permissions.ADMIN)) {
      return ADMIN;
    }
    Set<UUID> writable =
        idsGrantedBy(permissions, Permissions.EXPERIMENT_WRITE, writeExperimentIds);
    Set<UUID> readable =
        idsGrantedBy(permissions, Permissions.EXPERIMENT_READ, union(readExperimentIds, writable));
    return new ExperimentAccess(false, readable, writable);
  }

  private static Set<UUID> idsGrantedBy(
      Set<String> permissions, String permission, Collection<UUID> experimentIds) {
    return permissions.contains(permission) ? Set.copyOf(experimentIds) : Set.of();
  }

  private static Set<UUID> union(Collection<UUID> first, Collection<UUID> second) {
    return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
  }
}
