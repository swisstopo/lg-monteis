package ch.swisstopo.monteis.core.modules.demo.web.dto;

import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName("Sensor")
public record WriteSensorDto(
    @Id Long id,
    String code,
    Double upperBound,
    Double lowerBound,
    Integer version,
    String expression,
    Integer formulaVersion) {}
