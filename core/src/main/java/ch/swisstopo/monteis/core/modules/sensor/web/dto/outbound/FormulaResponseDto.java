package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import java.util.UUID;

public record FormulaResponseDto(UUID id, String expression, Integer version) {}
