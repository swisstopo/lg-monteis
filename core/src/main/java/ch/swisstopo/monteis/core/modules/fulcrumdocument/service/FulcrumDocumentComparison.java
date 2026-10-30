package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  // already removed excluded, keep first removedAt
  List<UUID> idsNoLongerInFulcrum() {
    Set<UUID> inFulcrum =
        fromFulcrum.stream().map(FulcrumLiteratureRow::recordId).collect(Collectors.toSet());
    return storedById.values().stream()
        .filter(FulcrumDocument::isAvailableInFulcrum)
        .map(FulcrumDocument::id)
        .filter(id -> !inFulcrum.contains(id))
        .toList();
  }

  List<NewFile> newFiles() {
    return fromFulcrum.stream()
        .flatMap(
            row ->
                row
                    .firstAttachment()
                    .filter(attachment -> !isStoredFile(row, attachment))
                    .map(attachment -> new NewFile(row, attachment))
                    .stream())
        .toList();
  }

  private boolean isStoredFile(FulcrumLiteratureRow row, FulcrumAttachmentReference attachment) {
    return storedDocumentOf(row).filter(stored -> stored.hasFile(attachment.id())).isPresent();
  }

  private Optional<FulcrumDocument> storedDocumentOf(FulcrumLiteratureRow row) {
    return Optional.ofNullable(storedById.get(row.recordId()));
  }

  record StoredRow(FulcrumLiteratureRow row, FulcrumDocument document) {}

  record NewFile(FulcrumLiteratureRow row, FulcrumAttachmentReference attachment) {}
}
