package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.SensorConfig;
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
      String sensorId, Double siValue, SensorConfig config, ProcessingOrigin origin) {
    if (siValue > config.getUpperBound()) {
      publishBreach(
          sensorId,
          siValue,
          config.getUpperBound(),
          SensorBoundBreachedEvent.BoundType.UPPER,
          origin);
      return BoundStatus.TOO_HIGH;
    }

    if (siValue < config.getLowerBound()) {
      publishBreach(
          sensorId,
          siValue,
          config.getLowerBound(),
          SensorBoundBreachedEvent.BoundType.LOWER,
          origin);
      return BoundStatus.TOO_LOW;
    }

    return BoundStatus.OK;
  }

  private void publishBreach(
      String sensorId,
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
        new SensorBoundBreachedEvent(sensorId, value, limit, type, Instant.now(clock)));
  }
}
