package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryDetails;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// every test rolls back, markRemovedUnlessSeenAt and findLastSeenAt look at the whole table
@IT
@Transactional
class JooqFulcrumEntryRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);

  @Autowired private JooqFulcrumEntryRepository repository;

  @Test
  void should_save_a_new_entry_with_its_document() {
    // given
    FulcrumEntryDetails details = details(UUID.randomUUID(), "2011-02");
    FulcrumDocument document = document();

    // when
    repository.saveWithDocument(details, document, FIRST_SYNC);

    // then
    FulcrumEntry saved = findById(details.id());
    assertThat(saved.details()).isEqualTo(details);
    assertThat(saved.document()).isEqualTo(document);
    assertThat(saved.isAvailableInFulcrum()).isTrue();
    assertThat(repository.getDocumentById(document.id())).isEqualTo(document);
    assertThat(repository.findDocumentIdsByEntry()).containsEntry(details.id(), document.id());
  }

  @Test
  void should_update_the_details_but_keep_the_document() {
    // given
    UUID id = UUID.randomUUID();
    FulcrumDocument document = document();
    repository.saveWithDocument(details(id, "2011-02"), document, FIRST_SYNC);

    // when
    repository.saveSeenDetails(List.of(details(id, "2011-03")), SECOND_SYNC);

    // then
    FulcrumEntry saved = findById(id);
    assertThat(saved.details().number()).isEqualTo("2011-03");
    assertThat(saved.document()).isEqualTo(document);
  }

  @Test
  void should_switch_an_entry_to_its_new_document() {
    // given
    UUID id = UUID.randomUUID();
    repository.saveWithDocument(details(id, "1"), document(), FIRST_SYNC);
    FulcrumDocument replacement = document();

    // when
    repository.saveWithDocument(details(id, "1"), replacement, SECOND_SYNC);

    // then
    assertThat(findById(id).document()).isEqualTo(replacement);
  }

  @Test
  void should_keep_an_entry_fulcrum_no_longer_returns_as_removed() {
    // given
    UUID kept = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    repository.saveWithDocument(details(kept, "1"), document(), FIRST_SYNC);
    repository.saveWithDocument(details(gone, "2"), document(), FIRST_SYNC);
    repository.saveSeenDetails(List.of(details(kept, "1")), SECOND_SYNC);

    // when
    int removed = repository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // then
    assertThat(removed).isEqualTo(1);
    assertThat(findById(gone).isAvailableInFulcrum()).isFalse();
    assertThat(findById(kept).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_make_an_entry_available_again_once_fulcrum_returns_it() {
    // given
    UUID id = UUID.randomUUID();
    repository.saveWithDocument(details(id, "1"), document(), FIRST_SYNC);
    repository.markRemovedUnlessSeenAt(SECOND_SYNC);

    // when
    repository.saveSeenDetails(List.of(details(id, "1")), SECOND_SYNC);

    // then
    assertThat(findById(id).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_report_when_entries_were_last_seen() {
    // given
    repository.saveWithDocument(details(UUID.randomUUID(), "1"), document(), SECOND_SYNC);

    // when / then
    assertThat(repository.findLastSeenAt())
        .hasValueSatisfying(lastSeen -> assertThat(lastSeen).isAfterOrEqualTo(SECOND_SYNC));
  }

  @Test
  void should_list_the_newest_number_first() {
    // given
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    repository.saveWithDocument(details(older, "2011-02"), document(), FIRST_SYNC);
    repository.saveWithDocument(details(newer, "2015-17"), document(), FIRST_SYNC);

    // when
    List<UUID> order =
        repository.findAll().stream()
            .map(entry -> entry.details().id())
            .filter(id -> id.equals(older) || id.equals(newer))
            .toList();

    // then
    assertThat(order).containsExactly(newer, older);
  }

  @Test
  void should_not_find_an_unknown_document() {
    // when / then
    assertThrows(
        ObjectNotFoundException.class, () -> repository.getDocumentById(UUID.randomUUID()));
  }

  private FulcrumEntry findById(UUID id) {
    return repository.findAll().stream()
        .filter(entry -> entry.details().id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static FulcrumEntryDetails details(UUID id, String number) {
    return new FulcrumEntryDetails(
        id,
        number,
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"));
  }

  private static FulcrumDocument document() {
    return new FulcrumDocument(
        UUID.randomUUID(), new DocumentMetadata("report.pdf", "application/pdf", 42));
  }
}
