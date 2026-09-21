package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.junit.jupiter.api.Assertions.*;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.AlarmLimits;
import ch.swisstopo.monteis.core.modules.sensor.domain.Coordinates;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.jooq.JooqSensorRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *   <li>TEMP-1's parameter (alias temperature) — experiment "Mont Terri Alpha"
 *   <li>PRESS-1&amp;2's parameter (alias pressure) — experiments "Mont Terri Alpha"
 *       &amp; "Mont Terri Beta"
 *   <li>DISP-2's parameter (alias displacement) — experiment "Mont Terri Beta"
 *   <li>FLOW-2's parameter (alias flow) — experiment "Mont Terri Beta"
 *   <li>FLOW-Admin's parameter (alias flow) — no experiment, admin-only
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

  @Autowired private JooqSensorRepository sensorRepository;

  private final OffsetDateTime wideFrom = OffsetDateTime.now().minusDays(1);
  private final OffsetDateTime wideTo = OffsetDateTime.now().plusMinutes(5);

  @Test
  @Transactional
  void should_fetch_measurements_for_single_sensor_within_wide_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          Optional<ChartDataResponseDto> result =
              repository.findChartData(TEMP_1_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          ChartDataResponseDto dto = result.get();
          assertEquals(TEMP_1_PARAM, dto.id());
          assertEquals("SOL_EXPERTS__TEMP-1__temperature", dto.dasKey());
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
                  .where(SENSOR_READING_SECURED.DAS_KEY.eq("SOL_EXPERTS__TEMP-1__temperature"))
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
              repository.findChartData(TEMP_1_PARAM, wideFrom, wideTo);
          Optional<ChartDataResponseDto> disp =
              repository.findChartData(DISP_2_PARAM, wideFrom, wideTo);

          // Assert
          assertEquals("SOL_EXPERTS__TEMP-1__temperature", temp.orElseThrow().dasKey());
          assertEquals("SOL_EXPERTS__DISP-2__displacement", disp.orElseThrow().dasKey());
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
              repository.findChartData(TEMP_1_PARAM, futureFrom, futureTo);

          // Assert: the sensor itself is still returned, just with no data points
          assertTrue(result.isPresent());
          assertTrue(result.get().points().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_return_empty_when_id_does_not_exist() {
    SecurityContextTestSupport.runAsAdmin(
        () -> assertTrue(repository.findChartData(NON_EXISTENT_ID, wideFrom, wideTo).isEmpty()));
  }

  @Test
  @Transactional
  void should_exclude_admin_only_sensor_for_regular_user_without_access() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        // FLOW-Admin belongs to no experiment, explicitly requesting its id must not help
        () -> assertTrue(repository.findChartData(FLOW_ADMIN_PARAM, wideFrom, wideTo).isEmpty()));
  }

  @Test
  @Transactional
  void should_include_sensor_for_regular_user_with_matching_experiment() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // Act: TEMP-1 belongs to experiment 1
          Optional<ChartDataResponseDto> result =
              repository.findChartData(TEMP_1_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          assertEquals("SOL_EXPERTS__TEMP-1__temperature", result.get().dasKey());
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
              repository.findChartData(FLOW_ADMIN_PARAM, wideFrom, wideTo);

          // Assert
          assertTrue(result.isPresent());
          assertEquals("SOL_EXPERTS__FLOW-Admin__flow", result.get().dasKey());
          assertFalse(result.get().points().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_exclude_sensor_from_other_experiment_for_regular_user() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        // DISP-2 belongs only to experiment 2
        () -> assertTrue(repository.findChartData(DISP_2_PARAM, wideFrom, wideTo).isEmpty()));
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
              dsl.selectDistinct(SENSOR_READING_SECURED.DAS_KEY)
                  .from(SENSOR_READING_SECURED)
                  .fetch(SENSOR_READING_SECURED.DAS_KEY);

          assertEquals(
              List.of("SOL_EXPERTS__PRESS-1&2__pressure", "SOL_EXPERTS__TEMP-1__temperature"),
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
    return repository.findChartData(id, from, to).orElseThrow().points();
  }

  @Test
  @Transactional
  void should_map_all_fields_of_a_measurement_row() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__TEMP-1__temperature", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
          MeasurementResponseDto row = result.rows().getFirst();
          assertEquals(TEMP_1_PARAM, row.sensorParameterId());
          assertEquals("SOL_EXPERTS__TEMP-1__temperature", row.dasKey());
          assertEquals("Mont Terri Alpha", row.experimentName());
          assertEquals("monteis-001", row.sensorName());
          assertEquals("Temperature Param", row.sensorParameterName());
          assertNotNull(row.newestMeasurement());
          assertNotNull(row.measureValue());
          assertEquals("KELVIN", row.unit());
          assertEquals("Temperature", row.sensorType());
          // the seed places TEMP-1 at (-64.2, -94.0, 0.1), but x/y/z are INTEGER columns,
          // so the fractions are rounded away on insert
          assertEquals(-64.0, row.x());
          assertEquals(-94.0, row.y());
          assertEquals(0.0, row.z());
          assertEquals(-50.0, row.alarmLimitFrom());
          assertEquals(100.0, row.alarmLimitTo());
          assertEquals(true, row.active());
          assertEquals("Air temperature sensor near ventilation intake", row.comment());
          assertFalse(row.trend().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_trend_points_from_the_last_four_days_ordered_ascending() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__TEMP-1__temperature", null)));

          // when
          List<ChartPointDto> trend = repository.findPaged(request).rows().getFirst().trend();

          // then: seed data has readings every 5 minutes for the past year, so the trend window
          // (now - 4 days) is never empty
          assertFalse(trend.isEmpty());
          OffsetDateTime fourDaysAgo = OffsetDateTime.now().minusDays(4).minusMinutes(5);
          for (int i = 0; i < trend.size(); i++) {
            assertTrue(trend.get(i).timestamp().isAfter(fourDaysAgo));
            if (i > 0) {
              assertFalse(trend.get(i - 1).timestamp().isAfter(trend.get(i).timestamp()));
            }
          }
        });
  }

  /**
   * Trend is fetched via a separate batched query keyed by {@code sensor_parameter_id} and merged
   * back onto each row in Java (see {@code MeasurementQueryRepository.fetchTrends}) - this guards
   * against a grouping/zip bug that could silently swap or duplicate trend lists across rows of a
   * multi-row page.
   */
  @Test
  @Transactional
  void should_not_mix_up_trend_points_across_rows_of_a_multi_row_page() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: the 4 fixed "monteis-*" sensors, each with its own parameter and readings
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("sensorName", new TextFilterModel("contains", "monteis", null)));

          // when
          List<MeasurementResponseDto> rows = repository.findPaged(request).rows();

          // then: each row's trend matches exactly what an independent query for its own
          // sensor_parameter_id returns
          assertEquals(4, rows.size());
          for (MeasurementResponseDto row : rows) {
            OffsetDateTime trendFrom = OffsetDateTime.now().minusDays(4);
            List<ChartPointDto> expectedTrend =
                dsl.select(SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
                    .from(SENSOR_READING_SECURED)
                    .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(row.sensorParameterId()))
                    .and(SENSOR_READING_SECURED.TIMESTAMP.ge(trendFrom))
                    .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
                    .fetch(r -> new ChartPointDto(r.value1(), r.value2()));

            assertEquals(
                expectedTrend,
                row.trend(),
                "trend for " + row.sensorName() + " must match its own sensor_parameter_id");
          }

          // and: sanity-check the rows don't all coincidentally share one identical trend list,
          // which would mask a bug that always grouped everything under a single key
          long distinctTrends = rows.stream().map(MeasurementResponseDto::trend).distinct().count();
          assertTrue(distinctTrends > 1, "fixture sanity check: sensors have distinct trends");
        });
  }

  @Test
  @Transactional
  void should_fall_back_to_composed_das_key_when_parameter_has_no_readings_yet() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a freshly created sensor/parameter has no matching row in TimescaleDB, so
          // DAS_KEY must fall back to composing it from current metadata
          Formula formula = new Formula();
          formula.setExpression("x");
          SensorParameter parameter =
              new SensorParameter(
                  "NoReadingFallbackParam",
                  "voltage",
                  new SensorType(null, "Other", null),
                  Unit.METER,
                  formula,
                  new AlarmLimits(0.0, 100.0),
                  true,
                  null);
          Sensor sensor =
              new Sensor(
                  "No Reading Sensor",
                  "NOREAD-1",
                  Das.SOL_EXPERTS,
                  null,
                  null,
                  new Coordinates(0, 0, 0),
                  true,
                  null);
          sensor.setParameters(new ArrayList<>(List.of(parameter)));
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "sensorParameterName",
                      new TextFilterModel("contains", "noreadingfallback", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
          MeasurementResponseDto row = result.rows().getFirst();
          assertEquals("SOL_EXPERTS__NOREAD-1__voltage", row.dasKey());
          assertNull(row.newestMeasurement());
          assertTrue(row.trend().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_default_sort_by_sensor_id_ascending_when_no_sort_model_given() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: narrows the 5 fixed seeded sensors down to the 4 named "monteis-*" (excludes
          // "ADMIN" and any bulk-* load-testing sensors)
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("sensorName", new TextFilterModel("contains", "monteis", null)));

          // when
          List<MeasurementResponseDto> rows = repository.findPaged(request).rows();

          // then: fixed sensor ids ascend TEMP-1 (...201) < PRESS-1&2 (...202) < DISP-2 (...203)
          // < FLOW-2 (...204)
          assertEquals(
              List.of("monteis-001", "monteis-002", "monteis-003", "monteis-004"),
              rows.stream().map(MeasurementResponseDto::sensorName).toList());
        });
  }

  @Test
  @Transactional
  void should_honor_an_explicit_sort_model_over_the_default() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(new SortModelItem("sensorName", SortDirection.DESC)),
                  Map.of("sensorName", new TextFilterModel("contains", "monteis", null)));

          // when
          List<MeasurementResponseDto> rows = repository.findPaged(request).rows();

          // then
          assertEquals(
              List.of("monteis-004", "monteis-003", "monteis-002", "monteis-001"),
              rows.stream().map(MeasurementResponseDto::sensorName).toList());
        });
  }

  @Test
  @Transactional
  void should_cap_rows_at_the_requests_limit_while_total_count_reflects_all_matches() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  2,
                  List.of(),
                  Map.of("sensorName", new TextFilterModel("contains", "monteis", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(4, result.totalCount(), "totalCount reflects all matching rows");
          assertEquals(2, result.rows().size(), "but only the requested page size is returned");
        });
  }

  @Test
  @Transactional
  void should_exclude_admin_only_sensor_from_paged_results_for_regular_user() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // given: FLOW-Admin belongs to no experiment, explicitly filtering for it must not help
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__FLOW-Admin__flow", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(0, result.totalCount());
          assertTrue(result.rows().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_include_admin_only_sensor_in_paged_results_for_admin() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__FLOW-Admin__flow", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
          MeasurementResponseDto row = result.rows().getFirst();
          assertEquals("ADMIN", row.sensorName());
          assertNull(row.experimentName());
        });
  }

  @Test
  @Transactional
  void should_include_sensor_in_paged_results_for_regular_user_with_matching_experiment() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // given: TEMP-1 belongs to experiment 1
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__TEMP-1__temperature", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
          assertEquals("Mont Terri Alpha", result.rows().getFirst().experimentName());
        });
  }

  @Test
  @Transactional
  void should_exclude_sensor_from_other_experiment_from_paged_results_for_regular_user() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // given: DISP-2 belongs only to experiment 2
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "dasKey",
                      new TextFilterModel("equals", "SOL_EXPERTS__DISP-2__displacement", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(0, result.totalCount());
          assertTrue(result.rows().isEmpty());
        });
  }

  @Test
  @Transactional
  void should_return_empty_page_when_filter_matches_nothing() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "sensorName",
                      new TextFilterModel("contains", "no-such-sensor-exists", null)));

          // when
          PagedResult<MeasurementResponseDto> result = repository.findPaged(request);

          // then
          assertEquals(0, result.totalCount());
          assertTrue(result.rows().isEmpty());
        });
  }
}
