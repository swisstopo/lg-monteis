package ch.swisstopo.monteis.core.modules.notification.service;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class RecipientService {

  private final ExperimentRepository experimentRepository;
  private final RecipientRepository recipientRepository;
  private final UserDirectory userDirectory;

  public RecipientService(
      ExperimentRepository experimentRepository,
      RecipientRepository recipientRepository,
      UserDirectory userDirectory) {
    this.experimentRepository = experimentRepository;
    this.recipientRepository = recipientRepository;
    this.userDirectory = userDirectory;
  }

  public Recipients findRecipientsOf(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    ExperimentMembers members = userDirectory.findMembersOf(experimentId);
    return recipientRepository
        .findSavedRecipientIdsOf(experimentId)
        .map(savedIds -> Recipients.savedAmong(members, savedIds))
        .orElseGet(() -> Recipients.pisUntilSaved(members));
  }

  public Recipients replaceRecipientsOf(UUID experimentId, Set<UUID> userIds) {
    experimentRepository.requireVisible(experimentId);
    // asked before the write: keycloak down => 503 with nothing stored
    ExperimentMembers members = userDirectory.findMembersOf(experimentId);
    // lost access => hidden from the list, kept so they count again once access is back
    Set<UUID> hiddenIds =
        recipientRepository
            .findSavedRecipientIdsOf(experimentId)
            .map(members::nonMembersAmong)
            .orElse(Set.of());
    recipientRepository.replaceRecipientsOf(experimentId, union(userIds, hiddenIds));
    return Recipients.savedAmong(members, userIds);
  }

  private static Set<UUID> union(Set<UUID> first, Set<UUID> second) {
    return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
  }
}
