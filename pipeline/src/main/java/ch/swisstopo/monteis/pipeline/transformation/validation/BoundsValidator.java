package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BoundsValidator {

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public BoundsValidator(ApplicationEventPublisher eventPublisher, Clock clock) {
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public BoundStatus evaluateBounds(
      String dasKey, Double siValue, SensorParameterConfig config, ProcessingOrigin origin) {
    if (siValue > config.getUpperBound()) {
      publishBreach(
          dasKey,
          siValue,
          config.getUpperBound(),
          SensorBoundBreachedEvent.BoundType.UPPER,
          origin);
      return BoundStatus.TOO_HIGH;
    }

    if (siValue < config.getLowerBound()) {
      publishBreach(
          dasKey,
          siValue,
          config.getLowerBound(),
          SensorBoundBreachedEvent.BoundType.LOWER,
          origin);
      return BoundStatus.TOO_LOW;
    }

    return BoundStatus.OK;
  }

  private void publishBreach(
      String dasKey,
      Double value,
      Double limit,
      SensorBoundBreachedEvent.BoundType type,
      ProcessingOrigin origin) {
    // Reprocessed historical readings must not re-trigger alerts that already fired (or
    // correctly didn't) when the value was first ingested.
    if (origin == ProcessingOrigin.REPROCESS) {
      return;
    }

    eventPublisher.publishEvent(
        new SensorBoundBreachedEvent(dasKey, value, limit, type, Instant.now(clock)));
  }
}
