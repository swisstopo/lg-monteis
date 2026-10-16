package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FulcrumSyncRun(
    UUID id,
    FulcrumSyncStatus status,
    String triggeredBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Integer entriesSeen,
    Integer documentsStored,
    Integer documentsFailed) {

  public static final String TRIGGERED_BY_SCHEDULE = "schedule";
}
