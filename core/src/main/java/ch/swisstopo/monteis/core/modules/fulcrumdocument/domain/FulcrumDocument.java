package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// id is Fulcrum's record id. removedAt is set once Fulcrum stopped returning the entry, it stays in
// MONTEIS as a backup
public record FulcrumDocument(
    UUID id,
    String number,
    String title,
    String authors,
    Integer year,
    String category,
    List<String> keywords,
    OffsetDateTime removedAt,
    List<FulcrumDocumentFile> files) {

  public FulcrumDocument {
    keywords = List.copyOf(keywords);
    files = List.copyOf(files);
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
