package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryWithDocuments;
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
class JooqFulcrumEntryRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);
  private static final DocumentMetadata REPORT =
      new DocumentMetadata("report.pdf", "application/pdf", 42);

  @Autowired private JooqFulcrumEntryRepository entryRepository;
  @Autowired private JooqFulcrumDocumentRepository documentRepository;

  @Test
  void should_save_a_new_entry_with_its_attachments_in_fulcrum_order() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumEntry entry = entry(entryId, "2011-02", attachment(entryId), attachment(entryId));

    // when
    entryRepository.saveSeen(List.of(entry), FIRST_SYNC);

    // then
    FulcrumEntryWithDocuments saved = findById(entryId);
    assertThat(saved.entry().number()).isEqualTo("2011-02");
    assertThat(saved.entry().keywords()).containsExactly("Experiment DS", "RACOS");
    assertThat(saved.entry().attachments()).isEqualTo(entry.attachments());
    assertThat(saved.entry().isAvailableInFulcrum()).isTrue();
    assertThat(saved.documents()).isEmpty();
  }

  @Test
  void should_update_a_known_entry() {
    // given
    UUID entryId = UUID.randomUUID();
    entryRepository.saveSeen(List.of(entry(entryId, "2011-02")), FIRST_SYNC);

    // when
    entryRepository.saveSeen(List.of(entry(entryId, "2011-03")), SECOND_SYNC);

    // then
    assertThat(findById(entryId).entry().number()).isEqualTo("2011-03");
  }

  @Test
  void should_keep_an_entry_fulcrum_no_longer_returns_as_removed() {
    // given
    UUID keptId = UUID.randomUUID();
    UUID goneId = UUID.randomUUID();
    entryRepository.saveSeen(List.of(entry(keptId, "1"), entry(goneId, "2")), FIRST_SYNC);
    entryRepository.saveSeen(List.of(entry(keptId, "1")), SECOND_SYNC);

    // when
    int removed = entryRepository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // then
    assertThat(removed).isEqualTo(1);
    assertThat(findById(goneId).entry().isAvailableInFulcrum()).isFalse();
    assertThat(findById(keptId).entry().isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_make_an_entry_available_again_once_fulcrum_returns_it() {
    // given
    UUID entryId = UUID.randomUUID();
    entryRepository.saveSeen(List.of(entry(entryId, "1")), FIRST_SYNC);
    entryRepository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // when
    entryRepository.saveSeen(List.of(entry(entryId, "1")), SECOND_SYNC);

    // then
    assertThat(findById(entryId).entry().isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_hand_out_only_attachments_without_a_document_of_entries_in_fulcrum() {
    // given
    UUID entryId = UUID.randomUUID();
    UUID removedEntryId = UUID.randomUUID();
    FulcrumAttachment missing = attachment(entryId);
    FulcrumAttachment stored = attachment(entryId);
    FulcrumAttachment ofRemovedEntry = attachment(removedEntryId);
    entryRepository.saveSeen(
        List.of(entry(entryId, "1", missing, stored), entry(removedEntryId, "2", ofRemovedEntry)),
        FIRST_SYNC);
    entryRepository.saveSeen(List.of(entry(entryId, "1", missing, stored)), SECOND_SYNC);
    entryRepository.markRemovedUnlessSeenAt(SECOND_SYNC);
    documentRepository.create(stored.id(), entryId, REPORT);

    // when
    List<FulcrumAttachment> toStore = entryRepository.findAttachmentsToStore();

    // then
    assertThat(toStore).contains(missing).doesNotContain(stored, ofRemovedEntry);
  }

  @Test
  void should_list_the_documents_of_an_entry_in_attachment_order() {
    // given
    UUID entryId = UUID.randomUUID();
    FulcrumAttachment first = attachment(entryId);
    FulcrumAttachment second = attachment(entryId);
    entryRepository.saveSeen(List.of(entry(entryId, "1", first, second)), FIRST_SYNC);
    documentRepository.create(second.id(), entryId, REPORT);
    documentRepository.create(first.id(), entryId, REPORT);

    // when
    List<FulcrumDocument> documents = findById(entryId).documents();

    // then
    assertThat(documents).extracting(FulcrumDocument::id).containsExactly(first.id(), second.id());
  }

  @Test
  void should_list_the_newest_number_first() {
    // given
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    entryRepository.saveSeen(List.of(entry(older, "2011-02"), entry(newer, "2015-17")), FIRST_SYNC);

    // when
    List<UUID> order =
        entryRepository.findAllWithDocuments().stream()
            .map(entryWithDocuments -> entryWithDocuments.entry().id())
            .filter(id -> id.equals(older) || id.equals(newer))
            .toList();

    // then
    assertThat(order).containsExactly(newer, older);
  }

  private FulcrumEntryWithDocuments findById(UUID entryId) {
    return entryRepository.findAllWithDocuments().stream()
        .filter(entryWithDocuments -> entryWithDocuments.entry().id().equals(entryId))
        .findFirst()
        .orElseThrow();
  }

  static FulcrumEntry entry(UUID id, String number, FulcrumAttachment... attachments) {
    return new FulcrumEntry(
        id,
        number,
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        List.of(attachments),
        null);
  }

  static FulcrumAttachment attachment(UUID entryId) {
    return new FulcrumAttachment(UUID.randomUUID(), entryId, "report.pdf");
  }
}
