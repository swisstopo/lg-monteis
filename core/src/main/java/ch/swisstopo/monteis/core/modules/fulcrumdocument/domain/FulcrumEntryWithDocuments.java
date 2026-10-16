package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.util.List;

// documents in the order Fulcrum lists their attachments, an attachment still to be downloaded has
// none yet
public record FulcrumEntryWithDocuments(FulcrumEntry entry, List<FulcrumDocument> documents) {

  public FulcrumEntryWithDocuments {
    documents = List.copyOf(documents);
  }
}
