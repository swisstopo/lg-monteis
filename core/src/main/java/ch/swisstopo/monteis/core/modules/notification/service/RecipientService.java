package ch.swisstopo.monteis.core.modules.notification.service;

import static ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUsers.idsOutside;
import static ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUsers.rejectUsersOutside;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class RecipientService {

  private static final String USER_IDS_FIELD = "userIds";

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
    experimentRepository.requireWritable(experimentId);
    ExperimentMembers members = userDirectory.findMembersOf(experimentId);
    return recipientRepository
        .findSavedRecipientIdsOf(experimentId)
        .map(savedIds -> Recipients.savedAmong(members, savedIds))
        .orElseGet(() -> Recipients.pisUntilSaved(members));
  }

  public Recipients replaceRecipientsOf(UUID experimentId, Set<UUID> userIds) {
    experimentRepository.requireWritable(experimentId);
    // asked before the write: keycloak down => 503 with nothing stored
    ExperimentMembers members = userDirectory.findMembersOf(experimentId);
    List<DirectoryUser> candidates = Recipients.candidatesOf(members);
    rejectUsersOutside(userIds, candidates, USER_IDS_FIELD);
    // lost access => hidden from the list, kept so they count again once access is back
    Set<UUID> hiddenIds =
        recipientRepository
            .findSavedRecipientIdsOf(experimentId)
            .map(savedIds -> idsOutside(savedIds, candidates))
            .orElse(Set.of());
    recipientRepository.replaceRecipientsOf(experimentId, union(userIds, hiddenIds));
    return Recipients.savedAmong(members, userIds);
  }

  private static Set<UUID> union(Set<UUID> first, Set<UUID> second) {
    return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
  }
}
