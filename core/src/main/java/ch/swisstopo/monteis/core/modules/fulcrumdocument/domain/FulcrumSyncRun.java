package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FulcrumSyncRun(
    UUID id,
    FulcrumSyncStatus status,
    String triggeredBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Integer documentsSeen,
    Integer filesStored,
    Integer filesFailed) {

  public static final String TRIGGERED_BY_SCHEDULE = "schedule";
}
