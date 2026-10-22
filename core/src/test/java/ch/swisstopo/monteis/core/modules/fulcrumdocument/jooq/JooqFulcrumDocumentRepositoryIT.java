package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IT
@Transactional
class JooqFulcrumDocumentRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);

  @Autowired private JooqFulcrumDocumentRepository repository;

  @Test
  void should_save_a_new_document_with_its_file() {
    // given
    FulcrumDocument document = document(UUID.randomUUID(), "2011-02", file());

    // when
    repository.saveSynced(document);

    // then
    assertThat(repository.getById(document.id())).isEqualTo(document);
  }

  @Test
  void should_update_the_metadata_of_a_known_document() {
    // given
    UUID id = UUID.randomUUID();
    FulcrumDocumentFile file = file();
    repository.saveSynced(document(id, "2011-02", file));

    // when
    repository.saveSynced(document(id, "2011-03", file));

    // then
    FulcrumDocument saved = repository.getById(id);
    assertThat(saved.number()).isEqualTo("2011-03");
    assertThat(saved.file()).isEqualTo(file);
  }

  @Test
  void should_switch_a_document_to_its_new_file() {
    // given
    UUID id = UUID.randomUUID();
    repository.saveSynced(document(id, "1", file()));
    FulcrumDocumentFile replacement = file();

    // when
    repository.saveSynced(document(id, "1", replacement));

    // then
    assertThat(repository.getById(id).file()).isEqualTo(replacement);
  }

  @Test
  void should_keep_a_document_fulcrum_no_longer_returns_as_removed() {
    // given
    FulcrumDocument kept = document(UUID.randomUUID(), "1", file());
    FulcrumDocument gone = document(UUID.randomUUID(), "2", file());
    repository.saveSynced(kept);
    repository.saveSynced(gone);

    // when
    repository.markRemovedFromFulcrum(List.of(gone.id()), FIRST_SYNC);

    // then
    assertThat(repository.getById(gone.id()).removedAt()).isAtSameInstantAs(FIRST_SYNC);
    assertThat(repository.getById(kept.id()).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_keep_when_a_document_was_first_marked_as_removed() {
    // given
    FulcrumDocument gone = document(UUID.randomUUID(), "1", file());
    repository.saveSynced(gone);
    repository.markRemovedFromFulcrum(List.of(gone.id()), FIRST_SYNC);

    // when
    repository.markRemovedFromFulcrum(List.of(gone.id()), SECOND_SYNC);

    // then
    assertThat(repository.getById(gone.id()).removedAt()).isAtSameInstantAs(FIRST_SYNC);
  }

  @Test
  void should_save_a_number_and_a_category_longer_than_255_characters() {
    // given
    FulcrumDocument document =
        new FulcrumDocument(
            UUID.randomUUID(),
            "1".repeat(300),
            null,
            null,
            null,
            "c".repeat(300),
            List.of(),
            file(),
            null);

    // when
    repository.saveSynced(document);

    // then
    assertThat(repository.getById(document.id())).isEqualTo(document);
  }

  @Test
  void should_make_a_document_available_again_once_fulcrum_returns_it() {
    // given
    FulcrumDocument document = document(UUID.randomUUID(), "1", file());
    repository.saveSynced(document);
    repository.markRemovedFromFulcrum(List.of(document.id()), FIRST_SYNC);

    // when
    repository.saveSynced(document);

    // then
    assertThat(repository.getById(document.id()).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_list_the_newest_number_first_and_documents_without_a_number_last() {
    // given
    FulcrumDocument older = document(UUID.randomUUID(), "2011-02", file());
    FulcrumDocument newer = document(UUID.randomUUID(), "2015-17", file());
    FulcrumDocument withoutNumber = document(UUID.randomUUID(), null, file());
    List<UUID> ids = List.of(older.id(), newer.id(), withoutNumber.id());
    repository.saveSynced(older);
    repository.saveSynced(withoutNumber);
    repository.saveSynced(newer);

    // when
    List<UUID> order =
        repository.findAll().stream().map(FulcrumDocument::id).filter(ids::contains).toList();

    // then
    assertThat(order).containsExactly(newer.id(), older.id(), withoutNumber.id());
  }

  @Test
  void should_not_find_an_unknown_document() {
    // when / then
    assertThrows(ObjectNotFoundException.class, () -> repository.getById(UUID.randomUUID()));
  }

  private static FulcrumDocument document(
      UUID id, @Nullable String number, FulcrumDocumentFile file) {
    return new FulcrumDocument(
        id,
        number,
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        file,
        null);
  }

  private static FulcrumDocumentFile file() {
    return new FulcrumDocumentFile(
        UUID.randomUUID(), new DocumentMetadata("report.pdf", "application/pdf", 42));
  }
}
