package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAdminClient;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakGroup;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUnavailableException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUser;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The PIs of an experiment are the enabled members of the groups whose {@code
 * write_experiment_ids} attribute holds the experiment id (see the realm in {@code
 * docker/keycloak/realm}). We search by that attribute, never by the group name.
 *
 * <p>Keycloak answers with the caller's permissions, so the cache is per caller: what one user was
 * allowed to read must never be served to another.
 */
class KeycloakUserDirectory implements UserDirectory {

  static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";

  private record CacheKey(UUID caller, UUID experimentId) {}

  private final KeycloakAdminClient keycloak;
  private final CurrentUserProvider currentUser;
  private final Cache<CacheKey, List<DirectoryUser>> principalInvestigators;

  KeycloakUserDirectory(
      KeycloakAdminClient keycloak, CurrentUserProvider currentUser, Duration cacheTtl) {
    this.keycloak = keycloak;
    this.currentUser = currentUser;
    this.principalInvestigators = Caffeine.newBuilder().expireAfterWrite(cacheTtl).build();
  }

  @Override
  public List<DirectoryUser> principalInvestigatorsOf(UUID experimentId) {
    UUID caller =
        currentUser
            .currentSubject()
            .orElseThrow(
                () ->
                    new UserDirectoryUnavailableException(
                        "No caller to ask Keycloak for the PIs of experiment " + experimentId,
                        null));
    try {
      return principalInvestigators.get(
          new CacheKey(caller, experimentId), key -> loadPrincipalInvestigators(experimentId));
    } catch (KeycloakAccessDeniedException e) {
      throw new UserDirectoryAccessDeniedException(
          "Keycloak denied reading the PIs of experiment " + experimentId, e);
    } catch (KeycloakUnavailableException e) {
      throw new UserDirectoryUnavailableException(
          "Keycloak failed reading the PIs of experiment " + experimentId, e);
    }
  }

  private List<DirectoryUser> loadPrincipalInvestigators(UUID experimentId) {
    return keycloak.findGroupsByAttribute(WRITE_EXPERIMENT_IDS, experimentId.toString()).stream()
        .map(KeycloakGroup::id)
        .flatMap(groupId -> keycloak.groupMembers(groupId).stream())
        .filter(KeycloakUser::enabled)
        .collect(Collectors.toMap(KeycloakUser::id, Function.identity(), (first, _) -> first))
        .values()
        .stream()
        .map(KeycloakUserDirectory::toDirectoryUser)
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  private static DirectoryUser toDirectoryUser(KeycloakUser user) {
    return new DirectoryUser(user.id(), user.firstName(), user.lastName(), user.email());
  }
}
