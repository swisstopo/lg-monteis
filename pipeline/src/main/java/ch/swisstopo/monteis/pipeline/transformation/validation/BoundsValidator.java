package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.events.SensorBoundBreachedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class BoundsValidator {

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public BoundsValidator(ApplicationEventPublisher eventPublisher, Clock clock) {
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public BoundStatus evaluateBounds(String sensorId, Double siValue, SensorConfig config) {
    if (siValue > config.getUpperBound()) {
      publishBreach(
          sensorId, siValue, config.getUpperBound(), SensorBoundBreachedEvent.BoundType.UPPER);
      return BoundStatus.TOO_HIGH;
    }

    if (siValue < config.getLowerBound()) {
      publishBreach(
          sensorId, siValue, config.getLowerBound(), SensorBoundBreachedEvent.BoundType.LOWER);
      return BoundStatus.TOO_LOW;
    }

    return BoundStatus.OK;
  }

  private void publishBreach(
      String sensorId, Double value, Double limit, SensorBoundBreachedEvent.BoundType type) {
    eventPublisher.publishEvent(
        new SensorBoundBreachedEvent(sensorId, value, limit, type, Instant.now(clock)));
  }
}
