package ch.swisstopo.monteis.pipeline.transformation;

import java.time.OffsetDateTime;

public record ChunkProcessingResult(int processedCount, OffsetDateTime cursor) {}
