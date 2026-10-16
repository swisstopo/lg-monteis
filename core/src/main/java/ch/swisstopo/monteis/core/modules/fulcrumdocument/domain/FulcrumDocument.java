package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// a document from the Mont Terri literature in Fulcrum, id is Fulcrum's record id. Fulcrum leaves
// any of the metadata empty. only exists once its file is in storage. removedAt is set once Fulcrum
// stopped returning it, it stays in MONTEIS as a backup
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

  public Optional<OffsetDateTime> removedFromFulcrumAt() {
    return Optional.ofNullable(removedAt);
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
