package ch.swisstopo.monteis.core.modules.demo.web.dto;

public record WriteSensorDto(
    Long id,
    String code,
    Double upperBound,
    Double lowerBound,
    Integer version,
    String expression,
    Integer formulaVersion) {}
