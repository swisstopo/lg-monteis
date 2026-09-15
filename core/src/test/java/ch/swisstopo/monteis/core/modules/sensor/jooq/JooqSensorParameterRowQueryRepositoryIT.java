package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.query.NumberFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterRowResponseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) - these tests
 * exercise the (Sensor, SensorParameter) grain query itself, not row-level security.
 */
@IT
class JooqSensorParameterRowQueryRepositoryIT {
  @Autowired private JooqSensorRepository sensorRepository;

  @Autowired private JooqSensorParameterRowQueryRepository parameterRowQueryRepository;

  @Test
  @Transactional
  void should_return_one_row_per_parameter_for_a_multi_parameter_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          Sensor sensor = createDummySensor("ROW-MULTI-01", "UniqueRowMultiName", "x");
          sensor.setParameters(
              new ArrayList<>(
                  List.of(
                      buildParameter("Param One", "x", 0.0, 100.0),
                      buildParameter("Param Two", "x * 2", 0.0, 50.0))));
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquerowmulti", null)));

          // when
          PagedResult<SensorParameterRowResponseDto> result =
              parameterRowQueryRepository.findPaged(request);

          // then: 2 rows, same sensor identity, distinct parameters
          assertEquals(2, result.totalCount(), "totalCount counts rows, not sensors");
          assertEquals(2, result.rows().size());
          assertEquals(result.rows().get(0).sensorId(), result.rows().get(1).sensorId());
          assertEquals(
              List.of("Param One", "Param Two"),
              result.rows().stream().map(r -> r.parameter().name()).sorted().toList());
        });
  }

  @Test
  @Transactional
  void should_return_a_single_row_with_null_parameter_for_a_parameterless_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a sensor created with no parameters at all
          Sensor sensor = createDummySensor("ROW-NONE-01", "UniqueRowNoneName", "x");
          sensor.setParameters(new ArrayList<>());
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquerownone", null)));

          // when
          PagedResult<SensorParameterRowResponseDto> result =
              parameterRowQueryRepository.findPaged(request);

          // then: the sensor is not dropped by the LEFT JOIN, its one row just has no parameter
          assertEquals(1, result.totalCount());
          assertNull(result.rows().getFirst().parameter());
        });
  }

  @Test
  @Transactional
  void should_filter_by_a_parameter_level_field() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: two sensors, only one has a parameter matching the filter
          Sensor matching = createDummySensor("ROW-PARAM-FILTER-01", "ParamFilterSensorA", "x");
          matching.setParameters(
              new ArrayList<>(List.of(buildParameter("UniqueParamFilterName", "x", 0.0, 100.0))));
          sensorRepository.create(matching);

          Sensor nonMatching = createDummySensor("ROW-PARAM-FILTER-02", "ParamFilterSensorB", "x");
          nonMatching.setParameters(
              new ArrayList<>(List.of(buildParameter("Other Param Name", "x", 0.0, 100.0))));
          sensorRepository.create(nonMatching);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "parameter.name",
                      new TextFilterModel("contains", "uniqueparamfilter", null)));

          // when
          PagedResult<SensorParameterRowResponseDto> result =
              parameterRowQueryRepository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
          assertEquals("ROW-PARAM-FILTER-01", result.rows().getFirst().dasSensorAlias());
        });
  }

  @Test
  @Transactional
  void should_filter_by_a_parameter_level_number_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          Sensor sensor = createDummySensor("ROW-NUM-FILTER-01", "NumFilterSensor", "x");
          sensor.setParameters(
              new ArrayList<>(List.of(buildParameter("NumFilterParam", "x", 123.0, 456.0))));
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "name",
                      new TextFilterModel("contains", "NumFilterSensor", null),
                      "parameter.alarmLimits.lower",
                      new NumberFilterModel("inRange", 100.0, 200.0)));

          // when
          PagedResult<SensorParameterRowResponseDto> result =
              parameterRowQueryRepository.findPaged(request);

          // then
          assertEquals(1, result.totalCount());
        });
  }

  @Test
  @Transactional
  void should_default_sort_by_sensor_name_and_keep_a_sensors_rows_contiguous() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: two sensors, each with two parameters, filtered down to just these four rows
          Sensor sensorZ = createDummySensor("ROW-SORT-Z", "ZZZ_ROW_SORT_TEST", "x");
          sensorZ.setParameters(
              new ArrayList<>(
                  List.of(
                      buildParameter("Z-Param-A", "x", 0.0, 1.0),
                      buildParameter("Z-Param-B", "x", 0.0, 1.0))));
          sensorRepository.create(sensorZ);

          Sensor sensorA = createDummySensor("ROW-SORT-A", "AAA_ROW_SORT_TEST", "x");
          sensorA.setParameters(
              new ArrayList<>(
                  List.of(
                      buildParameter("A-Param-A", "x", 0.0, 1.0),
                      buildParameter("A-Param-B", "x", 0.0, 1.0))));
          sensorRepository.create(sensorA);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(), // no explicit sort -> falls back to the default, sensor name asc
                  Map.of("name", new TextFilterModel("contains", "_ROW_SORT_TEST", null)));

          // when
          List<SensorParameterRowResponseDto> rows =
              parameterRowQueryRepository.findPaged(request).rows();

          // then: AAA sorts before ZZZ, and each sensor's two rows are adjacent (contiguous),
          // which is what the grid's row-spanning relies on
          assertEquals(4, rows.size());
          assertEquals("AAA_ROW_SORT_TEST", rows.get(0).name());
          assertEquals("AAA_ROW_SORT_TEST", rows.get(1).name());
          assertEquals("ZZZ_ROW_SORT_TEST", rows.get(2).name());
          assertEquals("ZZZ_ROW_SORT_TEST", rows.get(3).name());
        });
  }

  @Test
  @Transactional
  void should_honor_an_explicit_sort_model_over_the_default() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          sensorRepository.create(createDummySensor("ROW-EXPLICIT-A", "AAA_EXPLICIT_SORT", "x"));
          sensorRepository.create(createDummySensor("ROW-EXPLICIT-Z", "ZZZ_EXPLICIT_SORT", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(new SortModelItem("name", SortDirection.DESC)),
                  Map.of("name", new TextFilterModel("contains", "_EXPLICIT_SORT", null)));

          // when
          List<SensorParameterRowResponseDto> rows =
              parameterRowQueryRepository.findPaged(request).rows();

          // then
          assertEquals(2, rows.size());
          assertTrue(rows.get(0).name().startsWith("ZZZ"));
          assertTrue(rows.get(1).name().startsWith("AAA"));
        });
  }

  @Test
  @Transactional
  void should_accept_sorting_by_das() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: "das" is a colId the grid marks sortable - COLUMNS_BY_COL_ID must resolve it
          // or PagedRequestJooqTranslator throws InvalidPagedRequestException("Unknown
          // sortable/filterable column: das") for every request sorted by this column.
          sensorRepository.create(createDummySensor("ROW-DAS-SORT-01", "DasSortSensor", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(new SortModelItem("das", SortDirection.ASC)),
                  Map.of("name", new TextFilterModel("contains", "DasSortSensor", null)));

          // when
          List<SensorParameterRowResponseDto> rows =
              parameterRowQueryRepository.findPaged(request).rows();

          // then
          assertEquals(1, rows.size());
        });
  }

  @Test
  @Transactional
  void should_cap_rows_at_the_requests_limit() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: one sensor with three parameters, but the request's limit only allows 2 rows
          Sensor sensor = createDummySensor("ROW-CAP-01", "RowCapSensor", "x");
          sensor.setParameters(
              new ArrayList<>(
                  List.of(
                      buildParameter("Cap Param One", "x", 0.0, 1.0),
                      buildParameter("Cap Param Two", "x", 0.0, 1.0),
                      buildParameter("Cap Param Three", "x", 0.0, 1.0))));
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0, 2, List.of(), Map.of("name", new TextFilterModel("contains", "RowCap", null)));

          // when
          PagedResult<SensorParameterRowResponseDto> result =
              parameterRowQueryRepository.findPaged(request);

          // then
          assertEquals(3, result.totalCount(), "totalCount reflects all matching rows");
          assertEquals(2, result.rows().size(), "but only the requested page size is returned");
        });
  }

  private SensorParameter buildParameter(
      String name, String formulaExpression, double lower, double upper) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    return new SensorParameter(
        null,
        name,
        null,
        new SensorType(null, "Other", null),
        Unit.METER,
        formula,
        new AlarmLimits(lower, upper),
        true,
        null,
        null);
  }

  private Sensor createDummySensor(String code, String name, String formulaExpression) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    SensorParameter parameter =
        new SensorParameter(
            null,
            name,
            null,
            new SensorType(null, "Other", null),
            Unit.METER,
            formula,
            new AlarmLimits(0.0, 100.0),
            true,
            null,
            null);

    Sensor sensor =
        new Sensor(name, code, Das.SOL_EXPERTS, null, null, new Coordinates(0, 0, 0), true, null);
    sensor.setParameters(new ArrayList<>(List.of(parameter)));
    return sensor;
  }
}
