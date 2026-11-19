package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent.BoundType;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The alarm limits decide the measurement status and alert while notifications are active. The
 * special alarm limits only alert, while the sensor parameter is closely monitored.
 */
@Component
public class BoundsValidator {

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public BoundsValidator(ApplicationEventPublisher eventPublisher, Clock clock) {
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public MeasurementStatus evaluateBounds(
      String dasKey, Double siValue, SensorParameterConfig config, ProcessingOrigin origin) {
    if (Boolean.TRUE.equals(config.getNotificationActive())) {
      alertIfOutside(dasKey, siValue, config.getUpperBound(), config.getLowerBound(), origin);
    }
    if (Boolean.TRUE.equals(config.getCloselyMonitored())) {
      alertIfOutside(
          dasKey,
          siValue,
          config.getUpperSpecialAlarmLimit(),
          config.getLowerSpecialAlarmLimit(),
          origin);
    }

    if (siValue > config.getUpperBound()) {
      return MeasurementStatus.TOO_HIGH;
    }
    if (siValue < config.getLowerBound()) {
      return MeasurementStatus.TOO_LOW;
    }
    return MeasurementStatus.CORRECT;
  }

  private void alertIfOutside(
      String dasKey, Double siValue, Double upper, Double lower, ProcessingOrigin origin) {
    // Reprocessed historical readings must not re-trigger alerts that already fired (or
    // correctly didn't) when the value was first ingested.
    if (origin == ProcessingOrigin.REPROCESS) {
      return;
    }

    if (siValue > upper) {
      publishBreach(dasKey, siValue, upper, BoundType.UPPER);
    } else if (siValue < lower) {
      publishBreach(dasKey, siValue, lower, BoundType.LOWER);
    }
  }

  private void publishBreach(String dasKey, Double value, Double limit, BoundType type) {
    eventPublisher.publishEvent(
        new SensorBoundBreachedEvent(dasKey, value, limit, type, Instant.now(clock)));
  }
}
