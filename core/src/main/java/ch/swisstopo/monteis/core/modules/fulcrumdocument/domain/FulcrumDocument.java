package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;

// the one file of a Fulcrum entry, its content lives in FulcrumDocumentStorage. id is Fulcrum's
// attachment id
public record FulcrumDocument(UUID id, DocumentMetadata metadata) {}
