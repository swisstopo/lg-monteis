package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.junit.jupiter.api.Assertions.*;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link MeasurementQueryRepository} against the seeded dev dataset (see {@code
 * db/meta/seed} and {@code db/timescale/seed}). {@code findMeasurements} takes a {@code
 * sensor_parameter.id}, not a sensor id - each sensor below is seeded with exactly one parameter:
 *
 * <ul>
 *   <li>TEMP-1's parameter (alias TEMP-1-P1) — experiment "Mont Terri Alpha"
 *   <li>PRESS-1&amp;2's parameter (alias PRESS-1&amp;2-P1) — experiments "Mont Terri Alpha"
 *       &amp; "Mont Terri Beta"
 *   <li>DISP-2's parameter (alias DISP-2-P1) — experiment "Mont Terri Beta"
 *   <li>FLOW-2's parameter (alias FLOW-2-P1) — experiment "Mont Terri Beta"
 *   <li>FLOW-Admin's parameter (alias FLOW-Admin-P1) — no experiment, admin-only
 * </ul>
 *
 * <p>Each of these sensors has readings spaced 5 minutes apart, spanning the 365 days before the
 * seed migration ran, so a sufficiently wide {@code [from, to]} window reliably covers all of them
 * regardless of when in the test run this class executes. Tests run as admin unless the
 * row-level-security behavior itself is under test.
 *
 * <p>Access is still governed at the sensor level: {@code sensor_parameter} carries the same
 * row-level security as {@code sensors} (filtered via {@code can_access_sensor(sensor_id)}), so a
 * parameter is visible exactly when its parent sensor is. The repository reads one parameter per
 * call and returns an empty {@link Optional} both when the parameter does not exist and when
 * row-level security hides it, so that the API cannot be used to probe for the existence of
 * sensors the caller may not see.
 */
@IT
class MeasurementQueryRepositoryIT {

  // uuids match the seeding script
  private static final UUID TEMP_1_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000401");
  private static final UUID DISP_2_PARAM = UUID.fromString("00000000-0000-7000-8000-000000000403");
  private static final UUID FLOW_ADMIN_PARAM =
      UUID.fromString("00000000-0000-7000-8000-000000000405");
  private static final UUID NON_EXISTENT_ID = UUID.randomUUID();
  private static final List<UUID> EXPERIMENT_1_ONLY =
      List.of(UUID.fromString("00000000-0000-7000-8000-000000000301"));

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
          Optional<ChartDataResponseDto> result =
              repository.findMeasurements(TEMP_1_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          ChartDataResponseDto dto = result.get();
          assertEquals(TEMP_1_PARAM, dto.id());
          assertEquals("TEMP-1-P1", dto.code());
          assertEquals("monteis-001 - Temperature Param", dto.name());
          assertFalse(dto.points().isEmpty(), "Seed script generates readings for TEMP-1");
        });
  }

  @Test
  @Transactional
  void should_return_points_ordered_ascending_by_timestamp() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ChartPointDto> points = dataOf(TEMP_1_PARAM, wideFrom, wideTo);

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
                  .where(SENSOR_READING_SECURED.SENSOR_ID.eq("TEMP-1-P1"))
                  .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
                  .limit(1)
                  .fetchOne();
          OffsetDateTime timestamp = earliestReading.value1();
          Double rawValue = earliestReading.value2();
          Double normValue = earliestReading.value3();
          // Seed formula is raw * 0.98 with raw in [20, 80], so they can never coincide
          assertNotEquals(rawValue, normValue, "Fixture assumption: raw and norm values differ");

          // Act: fetch exactly that one reading through the repository
          ChartPointDto point = dataOf(TEMP_1_PARAM, timestamp, timestamp).getFirst();

          // Assert
          assertEquals(normValue, point.value());
        });
  }

  @Test
  @Transactional
  void should_resolve_each_sensor_independently() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act: the endpoint serves one sensor per call, so each id resolves on its own
          Optional<ChartDataResponseDto> temp =
              repository.findMeasurements(TEMP_1_PARAM, wideFrom, wideTo);
          Optional<ChartDataResponseDto> disp =
              repository.findMeasurements(DISP_2_PARAM, wideFrom, wideTo);

          // Assert
          assertEquals("TEMP-1-P1", temp.orElseThrow().code());
          assertEquals("DISP-2-P1", disp.orElseThrow().code());
        });
  }

  @Test
  @Transactional
  void should_return_sensor_with_empty_data_when_no_readings_in_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange: a window far in the future, after every seeded reading
          OffsetDateTime futureFrom = OffsetDateTime.now().plusDays(10);
          OffsetDateTime futureTo = futureFrom.plusHours(1);

          // Act
          Optional<ChartDataResponseDto> result =
              repository.findMeasurements(TEMP_1_PARAM, futureFrom, futureTo);

          // Assert: the sensor itself is still returned, just with no data points
          assertTrue(result.isPresent());
          assertTrue(result.get().points().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_return_empty_when_id_does_not_exist() {
    SecurityContextTestSupport.runAsAdmin(
        () -> assertTrue(repository.findMeasurements(NON_EXISTENT_ID, wideFrom, wideTo).isEmpty()));
  }

  @Test
  @Transactional
  void should_exclude_admin_only_sensor_for_regular_user_without_access() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        // FLOW-Admin belongs to no experiment, explicitly requesting its id must not help
        () ->
            assertTrue(repository.findMeasurements(FLOW_ADMIN_PARAM, wideFrom, wideTo).isEmpty()));
  }

  @Test
  @Transactional
  void should_include_sensor_for_regular_user_with_matching_experiment() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // Act: TEMP-1 belongs to experiment 1
          Optional<ChartDataResponseDto> result =
              repository.findMeasurements(TEMP_1_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          assertEquals("TEMP-1-P1", result.get().code());
          assertFalse(result.get().points().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_admin_only_sensor_for_admin() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          Optional<ChartDataResponseDto> result =
              repository.findMeasurements(FLOW_ADMIN_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          assertEquals("FLOW-Admin-P1", result.get().code());
          assertFalse(result.get().points().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_exclude_sensor_from_other_experiment_for_regular_user() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        // DISP-2 belongs only to experiment 2
        () -> assertTrue(repository.findMeasurements(DISP_2_PARAM, wideFrom, wideTo).isEmpty()));
  }

  /**
   * Guards the row-level-security boundary at the reading level rather than the sensor level: a
   * caller who cannot see DISP-2 must also not see any of its readings leak into another sensor's
   * series through the secured view's join.
   */
  @Test
  @Transactional
  void should_not_leak_readings_of_invisible_sensors_through_the_secured_view() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          List<String> visibleParameterAliases =
              dsl.selectDistinct(SENSOR_READING_SECURED.SENSOR_ID)
                  .from(SENSOR_READING_SECURED)
                  .fetch(SENSOR_READING_SECURED.SENSOR_ID);

          assertEquals(
              List.of("PRESS-1&2-P1", "TEMP-1-P1"),
              visibleParameterAliases.stream().sorted().toList());
        });
  }

  @Test
  @Transactional
  void should_include_reading_exactly_at_from_and_to_boundary() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          ChartPointDto earliestPoint = dataOf(TEMP_1_PARAM, wideFrom, wideTo).getFirst();
          OffsetDateTime boundary = earliestPoint.timestamp();

          // Act: from == to == the reading's exact timestamp
          List<ChartPointDto> points = dataOf(TEMP_1_PARAM, boundary, boundary);

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
              dataOf(TEMP_1_PARAM, wideFrom, wideTo).getFirst().timestamp();

          // Act: `to` lands one microsecond (the column's precision) before the earliest reading
          List<ChartPointDto> points =
              dataOf(TEMP_1_PARAM, wideFrom, earliestTimestamp.minusNanos(1_000));

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
          OffsetDateTime latestTimestamp =
              dataOf(TEMP_1_PARAM, wideFrom, wideTo).getLast().timestamp();

          // Act: `from` lands one microsecond (the column's precision) after the latest reading
          List<ChartPointDto> points =
              dataOf(TEMP_1_PARAM, latestTimestamp.plusNanos(1_000), wideTo);

          // Assert
          assertTrue(points.isEmpty());
        });
  }

  private List<ChartPointDto> dataOf(UUID id, OffsetDateTime from, OffsetDateTime to) {
    return repository.findMeasurements(id, from, to).orElseThrow().points();
  }
}
