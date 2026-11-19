package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.runAsAdmin;
import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.runAsGlobalEditor;
import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.runAsUser;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_RECIPIENT_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.transaction.annotation.Transactional;

@IT
@Transactional
class JooqRecipientRepositoryIT {

  private static final UUID ALPHA = SeedData.EXPERIMENT_ALPHA;
  private static final UUID BETA = SeedData.EXPERIMENT_BETA;
  private static final UUID ANNA = UUID.randomUUID();
  private static final UUID CARL = UUID.randomUUID();

  @Autowired private JooqRecipientRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  void should_find_nothing_while_the_list_was_never_saved() {
    // when
    Optional<Set<UUID>> saved = callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA));

    // then
    assertThat(saved).isEmpty();
  }

  @Test
  void should_find_a_saved_empty_list() {
    // given
    runAsAdmin(() -> saveList(ALPHA));

    // when
    Optional<Set<UUID>> saved = callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA));

    // then
    assertThat(saved).contains(Set.of());
  }

  @Test
  void should_find_the_saved_recipients_of_the_experiment_only() {
    // given
    runAsAdmin(
        () -> {
          saveList(ALPHA, ANNA, CARL);
          saveList(BETA, CARL);
        });

    // when
    Optional<Set<UUID>> saved = callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA));

    // then
    assertThat(saved).contains(Set.of(ANNA, CARL));
  }

  @Test
  void should_replace_the_saved_list_as_a_whole() {
    // given
    runAsAdmin(() -> saveList(ALPHA, ANNA));

    // when
    runAsAdmin(() -> saveList(ALPHA, CARL));

    // then
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA))).contains(Set.of(CARL));
  }

  @Test
  void should_keep_the_list_saved_when_it_is_replaced_with_an_empty_one() {
    // given
    runAsAdmin(() -> saveList(ALPHA, ANNA, CARL));

    // when
    runAsAdmin(() -> saveList(ALPHA));

    // then
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA))).contains(Set.of());
  }

  @Test
  void should_block_a_reader_from_replacing_a_saved_list_and_keep_it() {
    // given
    runAsAdmin(() -> saveList(ALPHA, ANNA));

    // when
    runAsUser(
        List.of(ALPHA),
        () -> assertThrows(PermissionDeniedDataAccessException.class, () -> saveList(ALPHA, CARL)));

    // then
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA))).contains(Set.of(ANNA));
  }

  @Test
  void should_hide_the_list_from_a_user_without_access_to_the_experiment() {
    // given
    runAsAdmin(() -> saveList(ALPHA, ANNA));

    // when / then
    runAsUser(
        List.of(BETA),
        () -> {
          assertEquals(0, dsl.fetchCount(EXPERIMENT_RECIPIENT_LIST));
          assertEquals(0, dsl.fetchCount(EXPERIMENT_RECIPIENT));
        });
    runAsUser(
        List.of(ALPHA),
        () -> assertThat(repository.findSavedRecipientIdsOf(ALPHA)).contains(Set.of(ANNA)));
  }

  @Test
  void should_let_a_pi_and_a_global_editor_save_the_list() {
    // when
    runAsUser(List.of(ALPHA), List.of(ALPHA), () -> saveList(ALPHA, ANNA));
    runAsGlobalEditor(() -> saveList(BETA, CARL));

    // then
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA))).contains(Set.of(ANNA));
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(BETA))).contains(Set.of(CARL));
  }

  @Test
  void should_block_a_reader_from_saving_the_list() {
    runAsUser(
        List.of(ALPHA),
        () -> assertThrows(PermissionDeniedDataAccessException.class, () -> saveList(ALPHA)));
  }

  @Test
  void should_block_a_reader_from_adding_a_recipient_to_a_saved_list() {
    // given
    runAsAdmin(() -> saveList(ALPHA));

    // when / then
    runAsUser(
        List.of(ALPHA),
        () ->
            assertThrows(
                PermissionDeniedDataAccessException.class,
                () ->
                    dsl.insertInto(EXPERIMENT_RECIPIENT)
                        .set(EXPERIMENT_RECIPIENT.EXPERIMENT_ID, ALPHA)
                        .set(EXPERIMENT_RECIPIENT.USER_ID, ANNA)
                        .execute()));
  }

  @Test
  void should_keep_a_reader_from_removing_a_recipient() {
    // given
    runAsAdmin(() -> saveList(ALPHA, ANNA));

    // when
    runAsUser(
        List.of(ALPHA),
        () ->
            assertEquals(
                0,
                dsl.deleteFrom(EXPERIMENT_RECIPIENT)
                    .where(EXPERIMENT_RECIPIENT.EXPERIMENT_ID.eq(ALPHA))
                    .execute(),
                "the delete policy filters the rows instead of raising"));

    // then
    assertThat(callAsAdmin(() -> repository.findSavedRecipientIdsOf(ALPHA))).contains(Set.of(ANNA));
  }

  private void saveList(UUID experimentId, UUID... userIds) {
    repository.replaceRecipientsOf(experimentId, Set.of(userIds));
  }
}
