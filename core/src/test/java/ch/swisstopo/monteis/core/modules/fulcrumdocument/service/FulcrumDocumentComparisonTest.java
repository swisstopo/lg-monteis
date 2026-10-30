package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentComparison.NewFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentComparison.StoredRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class FulcrumDocumentComparisonTest {

  private static final UUID STORED_ID = UUID.randomUUID();
  private static final UUID FILE = UUID.randomUUID();

  @Test
  void should_pair_each_row_with_its_stored_document() {
    // given
    FulcrumLiteratureRow storedRow = row(STORED_ID, FILE);
    FulcrumLiteratureRow newRow = row(UUID.randomUUID(), UUID.randomUUID());
    FulcrumDocument stored = stored(STORED_ID, FILE, null);

    // when / then
    assertThat(comparison(List.of(storedRow, newRow), stored).rowsOfStoredDocuments())
        .containsExactly(new StoredRow(storedRow, stored));
  }

  @Test
  void should_find_the_available_documents_fulcrum_no_longer_returns() {
    // given
    UUID gone = UUID.randomUUID();
    FulcrumDocument alreadyRemoved =
        stored(UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.parse("2026-09-01T00:00Z"));

    // when / then
    assertThat(
            comparison(
                    List.of(row(STORED_ID, FILE)),
                    stored(STORED_ID, FILE, null),
                    stored(gone, UUID.randomUUID(), null),
                    alreadyRemoved)
                .idsNoLongerInFulcrum())
        .containsExactly(gone);
  }

  @Test
  void should_take_the_rows_of_new_documents_and_of_replaced_files() {
    // given
    FulcrumLiteratureRow unchanged = row(STORED_ID, FILE);
    UUID replacedId = UUID.randomUUID();
    FulcrumLiteratureRow replaced = row(replacedId, UUID.randomUUID());
    FulcrumLiteratureRow added = row(UUID.randomUUID(), UUID.randomUUID());

    // when / then
    assertThat(
            comparison(
                    List.of(unchanged, replaced, added),
                    stored(STORED_ID, FILE, null),
                    stored(replacedId, UUID.randomUUID(), null))
                .newFiles())
        .extracting(NewFile::row)
        .containsExactly(replaced, added);
  }

  @Test
  void should_skip_a_row_without_a_file() {
    // given
    FulcrumLiteratureRow withoutFile =
        new FulcrumLiteratureRow(
            UUID.randomUUID(), "2011-02", "title", "Jahns E.", 2011, "TN", null, null, null);

    // when / then
    assertThat(comparison(List.of(withoutFile)).newFiles()).isEmpty();
  }

  private static FulcrumDocumentComparison comparison(
      List<FulcrumLiteratureRow> fromFulcrum, FulcrumDocument... stored) {
    return FulcrumDocumentComparison.of(fromFulcrum, List.of(stored));
  }

  private static FulcrumLiteratureRow row(UUID recordId, UUID attachmentId) {
    return new FulcrumLiteratureRow(
        recordId,
        "2011-02",
        "title",
        "Jahns E.",
        2011,
        "TN",
        List.of(),
        List.of(attachmentId),
        List.of("report.pdf"));
  }

  private static FulcrumDocument stored(
      UUID id, UUID attachmentId, @Nullable OffsetDateTime removedAt) {
    return new FulcrumDocument(
        id,
        "2011-02",
        "title",
        "Jahns E.",
        2011,
        "TN",
        List.of(),
        new FulcrumDocumentFile(
            attachmentId, new DocumentMetadata("report.pdf", "application/pdf", 8)),
        removedAt);
  }
}
