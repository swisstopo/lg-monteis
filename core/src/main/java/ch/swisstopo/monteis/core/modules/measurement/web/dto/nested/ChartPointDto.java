package ch.swisstopo.monteis.core.modules.measurement.web.dto.nested;

import java.time.OffsetDateTime;

public record ChartPointDto(OffsetDateTime timestamp, Double value) {}
