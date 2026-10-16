package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// the bibliographic data of a Fulcrum entry, id is Fulcrum's record id. Fulcrum leaves any of it
// empty, except the id
public record FulcrumCitation(
    UUID id,
    @Nullable String number,
    @Nullable String title,
    @Nullable String authors,
    @Nullable Integer year,
    @Nullable String category,
    List<String> keywords) {

  public FulcrumCitation {
    keywords = List.copyOf(keywords);
  }
}
