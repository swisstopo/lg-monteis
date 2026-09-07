package ch.swisstopo.monteis.core.modules.overview.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadSimpleMetricDto(
    OffsetDateTime timestamp,
    String sensorParameterId,
    Double rawValue,
    Double normValue,
    Short version,
    String status,
    UUID metadataSensorId) {}
