package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT_LIST;

import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JooqRecipientRepository implements RecipientRepository {

  private final DSLContext dsl;

  public JooqRecipientRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Set<UUID>> findSavedRecipientIdsOf(UUID experimentId) {
    if (!isListSaved(experimentId)) {
      return Optional.empty();
    }
    return Optional.of(
        Set.copyOf(
            dsl.select(EXPERIMENT_RECIPIENT.USER_ID)
                .from(EXPERIMENT_RECIPIENT)
                .where(EXPERIMENT_RECIPIENT.EXPERIMENT_ID.eq(experimentId))
                .fetch(EXPERIMENT_RECIPIENT.USER_ID)));
  }

  private boolean isListSaved(UUID experimentId) {
    return dsl.fetchExists(
        EXPERIMENT_RECIPIENT_LIST, EXPERIMENT_RECIPIENT_LIST.EXPERIMENT_ID.eq(experimentId));
  }
}
