package ch.swisstopo.monteis.core.modules.sensor.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorParameterDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorTypeDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import org.junit.jupiter.api.Test;

class SensorWebMapperTest {

  private final SensorWebMapper mapper = new SensorWebMapperImpl();

  @Test
  void should_default_formula_to_identity_expression_when_null() {
    // when
    Formula formula = mapper.toDomain((WriteFormulaDto) null);

    // then
    assertThat(formula.getExpression()).isEqualTo("x");
  }

  @Test
  void should_map_formula_expression_when_present() {
    // when
    Formula formula = mapper.toDomain(new WriteFormulaDto("x * 2"));

    // then
    assertThat(formula.getExpression()).isEqualTo("x * 2");
  }

  @Test
  void should_default_to_identity_formula_when_sensor_parameter_formula_is_omitted() {
    // given: mirrors the frontend omitting `formula` entirely when the field is left blank
    WriteSensorParameterDto dto =
        new WriteSensorParameterDto(
            null,
            "Temperature Param",
            "TEMP-1-P1",
            Unit.KELVIN,
            new WriteSensorTypeDto("Temperature"),
            new AlarmLimitsDto(-50.0, 100.0),
            true,
            null,
            null);

    // when
    SensorParameter parameter = mapper.toDomain(dto);

    // then
    assertThat(parameter.getFormula()).isNotNull();
    assertThat(parameter.getFormula().getExpression()).isEqualTo("x");
  }
}
