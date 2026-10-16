package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq.JooqFulcrumEntryRepositoryIT.attachment;
import static ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq.JooqFulcrumEntryRepositoryIT.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// every test rolls back, reconcileWithEntries touches the whole table
@IT
@Transactional
class JooqFulcrumDocumentRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);
  private static final DocumentMetadata REPORT =
      new DocumentMetadata("report.pdf", "application/pdf", 42);

  @Autowired private JooqFulcrumEntryRepository entryRepository;
  @Autowired private JooqFulcrumDocumentRepository documentRepository;

  @Test
  void should_create_a_document_stored_now() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumAttachment attachment = attachment(entryId);
    entryRepository.saveSeen(List.of(entry(entryId, "1", attachment)), FIRST_SYNC);

    // when
    FulcrumDocument document = documentRepository.create(attachment.id(), entryId, REPORT);

    // then
    assertThat(document.id()).isEqualTo(attachment.id());
    assertThat(document.entryId()).isEqualTo(entryId);
    assertThat(document.metadata()).isEqualTo(REPORT);
    assertThat(document.storedAt()).isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES));
    assertThat(document.isAvailableInFulcrum()).isTrue();
    assertThat(documentRepository.getById(attachment.id())).isEqualTo(document);
  }

  @Test
  void should_mark_a_document_no_entry_lists_anymore_as_removed() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumAttachment kept = attachment(entryId);
    FulcrumAttachment dropped = attachment(entryId);
    entryRepository.saveSeen(List.of(entry(entryId, "1", kept, dropped)), FIRST_SYNC);
    documentRepository.create(kept.id(), entryId, REPORT);
    documentRepository.create(dropped.id(), entryId, REPORT);
    entryRepository.saveSeen(List.of(entry(entryId, "1", kept)), SECOND_SYNC);

    // when
    documentRepository.reconcileWithEntries();

    // then
    assertThat(documentRepository.getById(kept.id()).isAvailableInFulcrum()).isTrue();
    assertThat(documentRepository.getById(dropped.id()).isAvailableInFulcrum()).isFalse();
  }

  @Test
  void should_mark_the_documents_of_a_removed_entry_as_removed() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumAttachment attachment = attachment(entryId);
    entryRepository.saveSeen(List.of(entry(entryId, "1", attachment)), FIRST_SYNC);
    documentRepository.create(attachment.id(), entryId, REPORT);
    entryRepository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // when
    documentRepository.reconcileWithEntries();

    // then
    assertThat(documentRepository.getById(attachment.id()).isAvailableInFulcrum()).isFalse();
  }

  @Test
  void should_make_a_document_available_again_once_an_entry_lists_it() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumAttachment attachment = attachment(entryId);
    entryRepository.saveSeen(List.of(entry(entryId, "1", attachment)), FIRST_SYNC);
    documentRepository.create(attachment.id(), entryId, REPORT);
    entryRepository.saveSeen(List.of(entry(entryId, "1")), SECOND_SYNC);
    documentRepository.reconcileWithEntries();

    // when
    entryRepository.saveSeen(List.of(entry(entryId, "1", attachment)), SECOND_SYNC);
    documentRepository.reconcileWithEntries();

    // then
    assertThat(documentRepository.getById(attachment.id()).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_move_a_document_with_its_attachment_to_another_entry() {
    // given
    UUID fromId = UUID.randomUUID();
    UUID toId = UUID.randomUUID();
    FulcrumAttachment attachment = attachment(fromId);
    entryRepository.saveSeen(List.of(entry(fromId, "1", attachment)), FIRST_SYNC);
    documentRepository.create(attachment.id(), fromId, REPORT);
    entryRepository.saveSeen(
        List.of(entry(fromId, "1"), entry(toId, "2", attachment)), SECOND_SYNC);

    // when
    documentRepository.reconcileWithEntries();

    // then
    FulcrumDocument moved = documentRepository.getById(attachment.id());
    assertThat(moved.entryId()).isEqualTo(toId);
    assertThat(moved.isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_not_find_an_unknown_document() {
    // when / then
    assertThrows(
        ObjectNotFoundException.class, () -> documentRepository.getById(UUID.randomUUID()));
  }
}
