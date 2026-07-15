package ch.swisstopo.monteis.core.modules.overview.web.dto;

import java.time.OffsetDateTime;

public record ReadSimpleMetricDto(
    OffsetDateTime timestamp,
    String sensorId,
    Double rawValue,
    Double normValue,
    Short version,
    String status) {}
