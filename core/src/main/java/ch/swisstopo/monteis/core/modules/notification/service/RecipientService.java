package ch.swisstopo.monteis.core.modules.notification.service;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

  public List<DirectoryUser> findRecipientCandidatesOf(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    return userDirectory.findMembersOf(experimentId).recipientCandidates();
  }

  public Recipients replaceRecipientsOf(UUID experimentId, Set<UUID> userIds) {
    experimentRepository.requireVisible(experimentId);
    // asked before the write: keycloak down => 503 with nothing stored
    ExperimentMembers members = userDirectory.findMembersOf(experimentId);
    recipientRepository.replaceRecipientsOf(experimentId, userIds);
    return Recipients.savedAmong(members, userIds);
  }
}
