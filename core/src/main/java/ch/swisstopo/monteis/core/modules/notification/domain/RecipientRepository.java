package ch.swisstopo.monteis.core.modules.notification.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RecipientRepository {

  Optional<Set<UUID>> findSavedRecipientIdsOf(UUID experimentId);
}
