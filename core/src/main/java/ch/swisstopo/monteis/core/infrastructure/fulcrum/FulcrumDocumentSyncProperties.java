package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// the cron itself is read by @Scheduled from monteis.fulcrum.documents.sync-cron. the lock is kept
// alive while a sync runs, lockAtMostFor only says how long a pod that died blocks the next sync.
// a file above maxFileSize is not downloaded at all, a few of them would fill the pod's disk
@ConfigurationProperties("monteis.fulcrum.documents")
public record FulcrumDocumentSyncProperties(
    @DefaultValue("8m") Duration timeBudget,
    @DefaultValue("2m") Duration lockAtMostFor,
    @DefaultValue("200MB") DataSize maxFileSize) {}
