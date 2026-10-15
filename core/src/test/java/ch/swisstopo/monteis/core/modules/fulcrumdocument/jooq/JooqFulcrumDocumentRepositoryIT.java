package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// every test rolls back, markRemovedUnlessSeenAt touches the whole table
@IT
@Transactional
class JooqFulcrumDocumentRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);

  @Autowired private JooqFulcrumDocumentRepository repository;

  @Test
  void should_save_a_new_document_with_its_files_in_fulcrum_order() {
    // given
    UUID documentId = UUID.randomUUID();
    FulcrumDocument document = document(documentId, "2011-02", file(documentId), file(documentId));

    // when
    repository.saveSeen(List.of(document), FIRST_SYNC);

    // then
    FulcrumDocument saved = findById(documentId);
    assertThat(saved.number()).isEqualTo("2011-02");
    assertThat(saved.keywords()).containsExactly("Experiment DS", "RACOS");
    assertThat(saved.isAvailableInFulcrum()).isTrue();
    assertThat(saved.files())
        .extracting(FulcrumDocumentFile::id)
        .containsExactly(document.files().get(0).id(), document.files().get(1).id());
    assertThat(saved.files()).noneMatch(FulcrumDocumentFile::isStored);
  }

  @Test
  void should_update_the_metadata_but_keep_the_stored_content() {
    // given
    UUID documentId = UUID.randomUUID();
    FulcrumDocumentFile file = file(documentId);
    repository.saveSeen(List.of(document(documentId, "2011-02", file)), FIRST_SYNC);
    repository.markStored(file.id(), 42);

    // when
    repository.saveSeen(List.of(document(documentId, "2011-03", file)), SECOND_SYNC);

    // then
    FulcrumDocument saved = findById(documentId);
    assertThat(saved.number()).isEqualTo("2011-03");
    assertThat(saved.files())
        .singleElement()
        .satisfies(
            stored -> {
              assertThat(stored.isStored()).isTrue();
              assertThat(stored.sizeBytes()).isEqualTo(42);
            });
  }

  @Test
  void should_keep_what_fulcrum_no_longer_returns_as_removed() {
    // given
    UUID keptId = UUID.randomUUID();
    UUID goneId = UUID.randomUUID();
    FulcrumDocumentFile keptFile = file(keptId);
    FulcrumDocumentFile droppedFile = file(keptId);
    repository.saveSeen(
        List.of(document(keptId, "1", keptFile, droppedFile), document(goneId, "2", file(goneId))),
        FIRST_SYNC);
    repository.saveSeen(List.of(document(keptId, "1", keptFile)), SECOND_SYNC);

    // when
    int removed = repository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // then
    assertThat(removed).isEqualTo(1);
    assertThat(findById(goneId).isAvailableInFulcrum()).isFalse();
    assertThat(findById(goneId).files()).noneMatch(FulcrumDocumentFile::isAvailableInFulcrum);
    FulcrumDocument kept = findById(keptId);
    assertThat(kept.isAvailableInFulcrum()).isTrue();
    assertThat(kept.files())
        .extracting(FulcrumDocumentFile::id, FulcrumDocumentFile::isAvailableInFulcrum)
        .containsExactlyInAnyOrder(tuple(keptFile.id(), true), tuple(droppedFile.id(), false));
  }

  @Test
  void should_make_a_document_available_again_once_fulcrum_returns_it() {
    // given
    UUID documentId = UUID.randomUUID();
    FulcrumDocumentFile file = file(documentId);
    repository.saveSeen(List.of(document(documentId, "1", file)), FIRST_SYNC);
    repository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // when
    repository.saveSeen(List.of(document(documentId, "1", file)), SECOND_SYNC);

    // then
    FulcrumDocument saved = findById(documentId);
    assertThat(saved.isAvailableInFulcrum()).isTrue();
    assertThat(saved.files()).allMatch(FulcrumDocumentFile::isAvailableInFulcrum);
  }

  @Test
  void should_only_hand_out_files_that_are_neither_stored_nor_removed() {
    // given
    UUID documentId = UUID.randomUUID();
    FulcrumDocumentFile missing = file(documentId);
    FulcrumDocumentFile stored = file(documentId);
    FulcrumDocumentFile removed = file(documentId);
    repository.saveSeen(List.of(document(documentId, "1", missing, stored, removed)), FIRST_SYNC);
    repository.markStored(stored.id(), 1);
    repository.saveSeen(List.of(document(documentId, "1", missing, stored)), SECOND_SYNC);
    repository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // when
    List<FulcrumDocumentFile> toStore = repository.findFilesToStore();

    // then
    assertThat(toStore).extracting(FulcrumDocumentFile::id).contains(missing.id());
    assertThat(toStore)
        .extracting(FulcrumDocumentFile::id)
        .doesNotContain(stored.id(), removed.id());
  }

  @Test
  void should_list_the_newest_number_first() {
    // given
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    repository.saveSeen(
        List.of(document(older, "2011-02", file(older)), document(newer, "2015-17", file(newer))),
        FIRST_SYNC);

    // when
    List<UUID> order =
        repository.findAll().stream()
            .map(FulcrumDocument::id)
            .filter(id -> id.equals(older) || id.equals(newer))
            .toList();

    // then
    assertThat(order).containsExactly(newer, older);
  }

  @Test
  void should_not_find_an_unknown_file() {
    // when / then
    assertThrows(ObjectNotFoundException.class, () -> repository.getFileById(UUID.randomUUID()));
  }

  private FulcrumDocument findById(UUID documentId) {
    return repository.findAll().stream()
        .filter(document -> document.id().equals(documentId))
        .findFirst()
        .orElseThrow();
  }

  private static FulcrumDocument document(UUID id, String number, FulcrumDocumentFile... files) {
    return new FulcrumDocument(
        id,
        number,
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        null,
        List.of(files));
  }

  private static FulcrumDocumentFile file(UUID documentId) {
    return new FulcrumDocumentFile(
        UUID.randomUUID(), documentId, "report.pdf", "application/pdf", null, null, null);
  }
}
