package ch.swisstopo.monteis.core.modules.notification.service;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
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
        .map(savedIds -> new Recipients(members.withAccessAmong(savedIds), true))
        .orElseGet(() -> new Recipients(members.pis(), false));
  }
}
