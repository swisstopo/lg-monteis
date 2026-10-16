package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.util.UUID;

// a file Fulcrum lists for an entry, it becomes a FulcrumDocument once its content is stored
public record FulcrumAttachment(UUID id, UUID entryId, String fileName) {}
