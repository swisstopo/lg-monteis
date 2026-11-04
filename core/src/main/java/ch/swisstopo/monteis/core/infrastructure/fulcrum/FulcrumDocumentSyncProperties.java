package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// cron read by @Scheduled, not here. maxFileSize: a few huge files would fill the pod's disk
@ConfigurationProperties("monteis.fulcrum.documents")
public record FulcrumDocumentSyncProperties(
    @DefaultValue("8m") Duration timeBudget,
    @DefaultValue("2m") Duration lockAtMostFor,
    @DefaultValue("200MB") DataSize maxFileSize) {}
