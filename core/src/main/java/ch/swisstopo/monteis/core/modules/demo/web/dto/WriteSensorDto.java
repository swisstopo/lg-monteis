package ch.swisstopo.monteis.core.modules.demo.web.dto;

import ch.swisstopo.monteis.core.infrastructure.javers.Auditable;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

@TypeName(WriteSensorDto.JAVERS_TYPE_NAME)
public record WriteSensorDto(
    @Id Long id,
    String code,
    Double upperBound,
    Double lowerBound,
    Integer version,
    String expression,
    Integer formulaVersion)
    implements Auditable {

  public static final String JAVERS_TYPE_NAME = "Sensor";
}
