package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;

// the one file of a Fulcrum Document, its content lives in FulcrumDocumentStorage under Fulcrum's
// attachment id
public record FulcrumDocumentFile(UUID attachmentId, DocumentMetadata metadata) {}
