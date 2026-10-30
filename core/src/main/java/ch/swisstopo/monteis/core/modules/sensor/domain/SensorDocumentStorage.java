package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentStorage;

/**
 * Where the content of {@link SensorDocument}s is kept, the database only holds their metadata.
 */
public interface SensorDocumentStorage extends DocumentStorage {}
