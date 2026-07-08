package ch.swisstopo.monteis.core.infrastructure.error;

import java.util.Map;

public record ErrorDTO(
    String field, Object actualValue, String messageKey, Map<String, Object> params) {}
