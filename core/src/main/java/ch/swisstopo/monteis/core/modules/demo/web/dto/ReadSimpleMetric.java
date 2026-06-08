package ch.swisstopo.monteis.core.modules.demo.web.dto;

import java.time.OffsetDateTime;

public record ReadSimpleMetric(OffsetDateTime timestamp, Double val) {}
