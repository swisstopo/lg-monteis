package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BoundsValidator {

  private static final Logger log = LoggerFactory.getLogger(BoundsValidator.class);

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public BoundsValidator(ApplicationEventPublisher eventPublisher, Clock clock) {
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public MeasurementStatus evaluateBounds(
      String dasKey, Double siValue, SensorParameterConfig config, ProcessingOrigin origin) {
    if (siValue > config.getUpperBound()) {
      publishBreach(
          dasKey,
          siValue,
          config.getUpperBound(),
          SensorBoundBreachedEvent.BoundType.UPPER,
          origin);
      return MeasurementStatus.TOO_HIGH;
    }

    if (siValue < config.getLowerBound()) {
      publishBreach(
          dasKey,
          siValue,
          config.getLowerBound(),
          SensorBoundBreachedEvent.BoundType.LOWER,
          origin);
      return MeasurementStatus.TOO_LOW;
    }

    return MeasurementStatus.CORRECT;
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

    SensorBoundBreachedEvent event =
        new SensorBoundBreachedEvent(dasKey, value, limit, type, Instant.now(clock));

    try {
      eventPublisher.publishEvent(event);
    } catch (RuntimeException ex) {
      log.error(
          "Failed to publish bound breach alert for sensor {}: value {} exceeded {} bound of {} at"
              + " {}. Reading is still persisted.",
          dasKey,
          value,
          type,
          limit,
          event.timestamp(),
          ex);
    }
  }
}
