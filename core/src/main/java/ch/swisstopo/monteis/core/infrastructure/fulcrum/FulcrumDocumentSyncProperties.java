package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// the cron itself is read by @Scheduled from monteis.fulcrum.documents.sync-cron. a run stops on
// its own after timeBudget, well before the lock expires, otherwise a second pod could start
@ConfigurationProperties("monteis.fulcrum.documents")
public record FulcrumDocumentSyncProperties(
    @DefaultValue("8m") Duration timeBudget, @DefaultValue("10m") Duration lockAtMostFor) {}
