package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT_LIST;

import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
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

  @Override
  @Transactional
  public void replaceRecipientsOf(UUID experimentId, Set<UUID> userIds) {
    // insert first: its RLS check raises for a reader, the delete policy only filters silently
    markListSaved(experimentId);
    dsl.deleteFrom(EXPERIMENT_RECIPIENT)
        .where(EXPERIMENT_RECIPIENT.EXPERIMENT_ID.eq(experimentId))
        .and(EXPERIMENT_RECIPIENT.USER_ID.notIn(userIds))
        .execute();
    if (!userIds.isEmpty()) {
      dsl.insertInto(
              EXPERIMENT_RECIPIENT,
              EXPERIMENT_RECIPIENT.EXPERIMENT_ID,
              EXPERIMENT_RECIPIENT.USER_ID)
          .valuesOfRows(userIds.stream().map(userId -> DSL.row(experimentId, userId)).toList())
          .onConflictDoNothing()
          .execute();
    }
  }

  private void markListSaved(UUID experimentId) {
    dsl.insertInto(EXPERIMENT_RECIPIENT_LIST)
        .set(EXPERIMENT_RECIPIENT_LIST.EXPERIMENT_ID, experimentId)
        .onConflictDoNothing()
        .execute();
  }
}
