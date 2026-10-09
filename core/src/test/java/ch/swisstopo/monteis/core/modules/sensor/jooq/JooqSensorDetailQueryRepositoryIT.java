package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailResponseDto;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the seeded dev dataset (see {@code db/meta/seed} and {@code db/timescale/seed}):
 * every seeded parameter has readings.
 */
@IT
class JooqSensorDetailQueryRepositoryIT {

  // uuids match the seeding script
  private static final UUID TEMP_1_SENSOR = UUID.fromString("00000000-0000-7000-8000-000000000201");
  private static final UUID TEMP_1_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000401");
  // TEMP-1 belongs to experiment Alpha (...301) only
  private static final UUID EXPERIMENT_BETA =
      UUID.fromString("00000000-0000-7000-8000-000000000302");
  private static final LocalDate TODAY = LocalDate.of(2024, Month.JANUARY, 1);

  @Autowired private JooqSensorDetailQueryRepository repository;
  @Autowired private JooqSensorRepository sensorRepository;

  @Test
  @Transactional
  void should_return_sensor_with_parameters_and_their_latest_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // when
          SensorDetailResponseDto detail = repository.findById(TEMP_1_SENSOR, TODAY).orElseThrow();

          // then
          assertEquals(TEMP_1_SENSOR, detail.id());
          assertNotNull(detail.das());
          assertEquals(1, detail.parameters().size());
          SensorDetailParameterResponseDto temperature = detail.parameters().getFirst();
          assertEquals(TEMP_1_PARAM, temperature.id());
          assertNotNull(temperature.formula());
          assertNotNull(temperature.latestParameterReadingValue());
          assertNotNull(temperature.latestParameterReadingValue().status());
          assertNotNull(temperature.latestParameterReadingValue().timestamp());
        });
  }

  @Test
  @Transactional
  void should_return_sensor_without_parameters() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          Sensor sensor = sensorRepository.create(buildSensor("DETAIL-NONE"));

          // when
          SensorDetailResponseDto detail = repository.findById(sensor.getId(), TODAY).orElseThrow();

          // then
          assertEquals(sensor.getId(), detail.id());
          assertTrue(detail.parameters().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_return_parameter_without_readings_with_null_latest_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a freshly created parameter has no readings
          Sensor input = buildSensor("DETAIL-NO-READING");
          input.setParameters(new ArrayList<>(List.of(buildParameter())));
          Sensor sensor = sensorRepository.create(input);

          // when
          SensorDetailResponseDto detail = repository.findById(sensor.getId(), TODAY).orElseThrow();

          // then
          assertEquals(1, detail.parameters().size());
          assertNull(detail.parameters().getFirst().latestParameterReadingValue());
        });
  }

  @Test
  @Transactional
  void should_return_empty_for_unknown_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> assertTrue(repository.findById(UUID.randomUUID(), TODAY).isEmpty()));
  }

  @Test
  @Transactional
  void should_return_empty_for_sensor_hidden_by_row_level_security() {
    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_BETA),
        () -> assertTrue(repository.findById(TEMP_1_SENSOR, TODAY).isEmpty()));
  }

  private Sensor buildSensor(String name) {
    Sensor sensor =
        new Sensor(
            name, name, Das.SOL_EXPERTS, null, null, new Coordinates(0d, 0d, 0d), true, null);
    sensor.setParameters(new ArrayList<>());
    return sensor;
  }

  private SensorParameter buildParameter() {
    Formula formula = new Formula();
    formula.setExpression("x");
    return new SensorParameter(
        null,
        "param",
        null,
        new SensorType(null, "Other", null),
        Unit.METER,
        formula,
        new AlarmLimits(0.0, 100.0),
        true,
        null,
        null);
  }
}
