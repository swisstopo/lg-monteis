package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailResponseDto;
import java.time.LocalDate;
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

  @Autowired private JooqSensorDetailQueryRepository repository;

  @Test
  @Transactional
  void should_return_sensor_with_parameters_and_their_latest_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // when
          SensorDetailResponseDto detail =
              repository.findById(TEMP_1_SENSOR, LocalDate.now()).orElseThrow();

          // then
          assertEquals(TEMP_1_SENSOR, detail.id());
          assertNotNull(detail.das());
          assertTrue(detail.parameters().size() >= 1);
          SensorDetailParameterResponseDto temperature =
              detail.parameters().stream()
                  .filter(p -> TEMP_1_PARAM.equals(p.id()))
                  .findFirst()
                  .orElseThrow();
          assertNotNull(temperature.formula());
          assertNotNull(temperature.sensorParameterReading());
          assertEquals("temperature", temperature.sensorParameterReading().parameter());
          assertNotNull(temperature.sensorParameterReading().timestamp());
        });
  }

  @Test
  @Transactional
  void should_return_empty_for_unknown_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> assertTrue(repository.findById(UUID.randomUUID(), LocalDate.now()).isEmpty()));
  }
}
