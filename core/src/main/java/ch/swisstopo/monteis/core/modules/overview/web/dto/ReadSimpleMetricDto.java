package ch.swisstopo.monteis.core.modules.overview.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadSimpleMetricDto(
    OffsetDateTime timestamp,
    String dasKey,
    Double rawValue,
    Double normValue,
    Short version,
    String status,
    UUID sensorParameterId) {}
