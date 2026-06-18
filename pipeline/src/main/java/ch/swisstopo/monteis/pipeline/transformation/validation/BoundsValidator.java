package ch.swisstopo.monteis.pipeline.transformation.validation;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.transformation.events.SensorBoundBreachedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BoundsValidator {private final ApplicationEventPublisher eventPublisher;

    public BoundsValidator(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public RangeCategory evaluateBounds(String sensorId, Double siValue, SensorConfig config) {
        if (siValue > config.getUpperBound()) {
            publishBreach(sensorId, siValue, config.getUpperBound(), SensorBoundBreachedEvent.BoundType.UPPER);
            return RangeCategory.too_high;
        }

        if (siValue < config.getLowerBound()) {
            publishBreach(sensorId, siValue, config.getLowerBound(), SensorBoundBreachedEvent.BoundType.LOWER);
            return RangeCategory.too_low;
        }

        return RangeCategory.correct;
    }

    private void publishBreach(String sensorId, Double value, Double limit, SensorBoundBreachedEvent.BoundType type) {
        eventPublisher.publishEvent(new SensorBoundBreachedEvent(
                sensorId, value, limit, type, Instant.now()
        ));
    }
}
