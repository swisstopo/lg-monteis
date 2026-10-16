package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.util.List;
import java.util.UUID;

// the citation part of a Fulcrum entry, id is Fulcrum's record id
public record FulcrumEntryDetails(
    UUID id,
    String number,
    String title,
    String authors,
    Integer year,
    String category,
    List<String> keywords) {

  public FulcrumEntryDetails {
    keywords = List.copyOf(keywords);
  }
}
