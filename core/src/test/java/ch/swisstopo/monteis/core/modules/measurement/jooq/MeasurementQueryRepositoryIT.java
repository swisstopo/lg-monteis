package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link MeasurementQueryRepository} against the seeded dev dataset (see {@code
 * db/meta/seed} and {@code db/timescale/seed}):
 *
 * <ul>
 *   <li>TEMP-1 (id 1) — experiment 1
 *   <li>PRESS-1&amp;2 (id 2) — experiments 1 &amp; 2
 *   <li>DISP-2 (id 3) — experiment 2
 *   <li>FLOW-2 (id 4) — experiment 2
 *   <li>FLOW-Admin (id 5) — no experiment, admin-only
 * </ul>
 *
 * <p>Each of these sensors has readings spaced 5 minutes apart, spanning the 10 hours before the
 * seed migration ran, so a sufficiently wide {@code [from, to]} window reliably covers all of
 * them regardless of when in the test run this class executes. Tests run as admin unless the
 * row-level-security behavior itself is under test.
 */
@IT
class MeasurementQueryRepositoryIT {

  private static final Long TEMP_1 = 1L;
  private static final Long DISP_2 = 3L;
  private static final Long FLOW_ADMIN = 5L;
  private static final Long NON_EXISTENT_ID = 999_999L;
  private static final List<Long> EXPERIMENT_1_ONLY = List.of(1L);

  @Autowired private MeasurementQueryRepository repository;

  @Autowired private DSLContext dsl;

  private final OffsetDateTime wideFrom = OffsetDateTime.now().minusDays(1);
  private final OffsetDateTime wideTo = OffsetDateTime.now().plusMinutes(5);

  @Test
  @Transactional
  void should_fetch_measurements_for_single_sensor_within_wide_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(TEMP_1), wideFrom, wideTo);

          // Assert
          assertEquals(1, results.size());
          ChartDataResponseDto dto = results.getFirst();
          assertEquals(TEMP_1, dto.id());
          assertEquals("TEMP-1", dto.sensorCode());
          assertEquals("monteis-001", dto.sensorName());
          assertFalse(dto.data().isEmpty(), "Seed script generates readings for TEMP-1");
        });
  }

  @Test
  @Transactional
  void should_return_points_ordered_ascending_by_timestamp() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartPointDto> points =
              repository.findMeasurements(List.of(TEMP_1), wideFrom, wideTo).getFirst().data();

          // Assert
          for (int i = 0; i < points.size() - 1; i++) {
            OffsetDateTime current = points.get(i).timestamp();
            OffsetDateTime next = points.get(i + 1).timestamp();
            assertTrue(
                current.isBefore(next) || current.isEqual(next),
                "Point at index " + i + " must not be after the following point");
          }
        });
  }

  @Test
  @Transactional
  void should_return_norm_value_not_raw_value_as_chart_point_value() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange: read the earliest raw reading directly, bypassing the mapping under test
          Record3<OffsetDateTime, Double, Double> earliestReading =
              dsl.select(
                      SENSOR_READING_SECURED.TIMESTAMP,
                      SENSOR_READING_SECURED.RAW_VALUE,
                      SENSOR_READING_SECURED.NORM_VALUE)
                  .from(SENSOR_READING_SECURED)
                  .where(SENSOR_READING_SECURED.SENSOR_ID.eq("TEMP-1"))
                  .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
                  .limit(1)
                  .fetchOne();
          OffsetDateTime timestamp = earliestReading.value1();
          Double rawValue = earliestReading.value2();
          Double normValue = earliestReading.value3();
          // Seed formula is raw * 0.98 with raw in [20, 80], so they can never coincide
          assertNotEquals(rawValue, normValue, "Fixture assumption: raw and norm values differ");

          // Act: fetch exactly that one reading through the repository
          ChartPointDto point =
              repository
                  .findMeasurements(List.of(TEMP_1), timestamp, timestamp)
                  .getFirst()
                  .data()
                  .getFirst();

          // Assert
          assertEquals(normValue, point.value());
        });
  }

  @Test
  @Transactional
  void should_return_multiple_sensors_when_multiple_ids_given() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(TEMP_1, DISP_2), wideFrom, wideTo);

          // Assert
          assertEquals(2, results.size());
          List<String> codes = results.stream().map(ChartDataResponseDto::sensorCode).toList();
          assertTrue(codes.contains("TEMP-1"));
          assertTrue(codes.contains("DISP-2"));
        });
  }

  @Test
  @Transactional
  void should_include_sensor_with_empty_data_when_no_readings_in_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange: a window far in the future, after every seeded reading
          OffsetDateTime futureFrom = OffsetDateTime.now().plusDays(10);
          OffsetDateTime futureTo = futureFrom.plusHours(1);

          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(TEMP_1), futureFrom, futureTo);

          // Assert: the sensor itself is still returned, just with no data points
          assertEquals(1, results.size());
          assertTrue(results.getFirst().data().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_omit_sensor_entirely_when_id_does_not_exist() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(NON_EXISTENT_ID), wideFrom, wideTo);

          // Assert
          assertTrue(results.isEmpty());
        });
  }

  @Test
  @Transactional
  void should_return_empty_list_when_ids_is_empty() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(), wideFrom, wideTo);

          // Assert
          assertTrue(results.isEmpty());
        });
  }

  @Test
  @Transactional
  void should_only_return_requested_ids_even_when_other_sensors_exist() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(TEMP_1), wideFrom, wideTo);

          // Assert: seed data has 5+ sensors, only the requested one must come back
          assertEquals(1, results.size());
          assertEquals("TEMP-1", results.getFirst().sensorCode());
        });
  }

  @Test
  @Transactional
  void should_exclude_admin_only_sensor_for_regular_user_without_access() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // Act: FLOW-Admin belongs to no experiment, explicitly requesting its id must not help
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(FLOW_ADMIN), wideFrom, wideTo);

          // Assert
          assertTrue(results.isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_sensor_for_regular_user_with_matching_experiment() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // Act: TEMP-1 belongs to experiment 1
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(TEMP_1), wideFrom, wideTo);

          // Assert
          assertEquals(1, results.size());
          assertEquals("TEMP-1", results.getFirst().sensorCode());
          assertFalse(results.getFirst().data().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_admin_only_sensor_for_admin() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(FLOW_ADMIN), wideFrom, wideTo);

          // Assert
          assertEquals(1, results.size());
          assertEquals("FLOW-Admin", results.getFirst().sensorCode());
          assertFalse(results.getFirst().data().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_exclude_sensor_from_other_experiment_for_regular_user() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // Act: DISP-2 belongs only to experiment 2
          List<ChartDataResponseDto> results =
              repository.findMeasurements(List.of(DISP_2), wideFrom, wideTo);

          // Assert
          assertTrue(results.isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_reading_exactly_at_from_and_to_boundary() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          ChartPointDto earliestPoint =
              repository
                  .findMeasurements(List.of(TEMP_1), wideFrom, wideTo)
                  .getFirst()
                  .data()
                  .getFirst();
          OffsetDateTime boundary = earliestPoint.timestamp();

          // Act: from == to == the reading's exact timestamp
          List<ChartPointDto> points =
              repository.findMeasurements(List.of(TEMP_1), boundary, boundary).getFirst().data();

          // Assert
          assertEquals(1, points.size());
          assertEquals(earliestPoint, points.getFirst());
        });
  }

  @Test
  @Transactional
  void should_exclude_reading_when_to_is_one_microsecond_before_earliest_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          OffsetDateTime earliestTimestamp =
              repository
                  .findMeasurements(List.of(TEMP_1), wideFrom, wideTo)
                  .getFirst()
                  .data()
                  .getFirst()
                  .timestamp();

          // Act: `to` lands one microsecond (the column's precision) before the earliest reading
          List<ChartPointDto> points =
              repository
                  .findMeasurements(List.of(TEMP_1), wideFrom, earliestTimestamp.minusNanos(1_000))
                  .getFirst()
                  .data();

          // Assert
          assertTrue(points.isEmpty());
        });
  }

  @Test
  @Transactional
  void should_exclude_reading_when_from_is_one_microsecond_after_latest_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          List<ChartPointDto> allPoints =
              repository.findMeasurements(List.of(TEMP_1), wideFrom, wideTo).getFirst().data();
          OffsetDateTime latestTimestamp = allPoints.getLast().timestamp();

          // Act: `from` lands one microsecond (the column's precision) after the latest reading
          List<ChartPointDto> points =
              repository
                  .findMeasurements(List.of(TEMP_1), latestTimestamp.plusNanos(1_000), wideTo)
                  .getFirst()
                  .data();

          // Assert
          assertTrue(points.isEmpty());
        });
  }
}
