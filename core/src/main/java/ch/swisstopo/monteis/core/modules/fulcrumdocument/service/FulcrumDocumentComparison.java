package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// a row and a stored document belong together when the row's record id is the document's id
record FulcrumDocumentComparison(
    List<FulcrumLiteratureRow> fromFulcrum, Map<UUID, FulcrumDocument> storedById) {

  static FulcrumDocumentComparison of(
      List<FulcrumLiteratureRow> fromFulcrum, List<FulcrumDocument> stored) {
    return new FulcrumDocumentComparison(
        fromFulcrum,
        stored.stream().collect(Collectors.toMap(FulcrumDocument::id, Function.identity())));
  }

  List<StoredRow> rowsOfStoredDocuments() {
    return fromFulcrum.stream()
        .flatMap(row -> storedDocumentOf(row).map(stored -> new StoredRow(row, stored)).stream())
        .toList();
  }

  // stored documents still available that Fulcrum did not return this time
  List<UUID> idsNoLongerInFulcrum() {
    Set<UUID> inFulcrum =
        fromFulcrum.stream().map(FulcrumLiteratureRow::recordId).collect(Collectors.toSet());
    return storedById.values().stream()
        .filter(FulcrumDocument::isAvailableInFulcrum)
        .map(FulcrumDocument::id)
        .filter(id -> !inFulcrum.contains(id))
        .toList();
  }

  // a new document, or a stored one whose file Fulcrum replaced
  List<FulcrumLiteratureRow> rowsWithNewFile() {
    return fromFulcrum.stream().filter(this::hasNewFile).toList();
  }

  private boolean hasNewFile(FulcrumLiteratureRow row) {
    return row.firstAttachment()
        .filter(
            attachment ->
                storedDocumentOf(row).filter(stored -> stored.hasFile(attachment.id())).isEmpty())
        .isPresent();
  }

  private Optional<FulcrumDocument> storedDocumentOf(FulcrumLiteratureRow row) {
    return Optional.ofNullable(storedById.get(row.recordId()));
  }

  record StoredRow(FulcrumLiteratureRow row, FulcrumDocument document) {}
}
