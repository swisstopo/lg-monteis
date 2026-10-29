package ch.swisstopo.monteis.core.infrastructure.document;

import java.util.UUID;

public record DocumentKey(UUID parentId, UUID documentId) {}
