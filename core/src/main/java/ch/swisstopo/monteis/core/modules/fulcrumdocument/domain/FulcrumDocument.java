package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// removedAt: gone from Fulcrum, row kept as backup (PO)
public record FulcrumDocument(
    UUID id,
    @Nullable String number,
    @Nullable String title,
    @Nullable String authors,
    @Nullable Integer year,
    @Nullable String category,
    List<String> keywords,
    FulcrumDocumentFile file,
    @Nullable OffsetDateTime removedAt) {

  public FulcrumDocument {
    keywords = List.copyOf(keywords);
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }

  public boolean hasFile(UUID attachmentId) {
    return file.attachmentId().equals(attachmentId);
  }
}
