package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// id is Fulcrum's record id. removedAt is set once Fulcrum stopped returning the entry, it stays in
// MONTEIS as a backup
public record FulcrumEntry(
    UUID id,
    String number,
    String title,
    String authors,
    Integer year,
    String category,
    List<String> keywords,
    List<FulcrumAttachment> attachments,
    OffsetDateTime removedAt) {

  public FulcrumEntry {
    keywords = List.copyOf(keywords);
    attachments = List.copyOf(attachments);
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
