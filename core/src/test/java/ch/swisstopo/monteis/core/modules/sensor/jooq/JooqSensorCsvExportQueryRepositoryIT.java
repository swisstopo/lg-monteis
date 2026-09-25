package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) - these tests
 * exercise the CSV export query itself, not row-level security.
 *
 * <p>Same (Sensor, SensorParameter) row grain as {@link JooqSensorParameterRowQueryRepositoryIT}
 * (the grid's backing query): a sensor with N parameters yields N rows, a sensor with none still
 * yields exactly one row with blank parameter columns.
 */
@IT
class JooqSensorCsvExportQueryRepositoryIT {

  private static final String HEADER =
      "dasSensorAlias,name,das,fulcrumId,mainExperiment.name,coordinates.x,coordinates.y,"
          + "coordinates.z,active,comment,parameter.name,parameter.dasParameterAlias,"
          + "parameter.type.name,parameter.unit,parameter.formula.expression,"
          + "parameter.alarmLimits.lower,parameter.alarmLimits.upper,parameter.active,"
          + "parameter.comment";

  @Autowired private JooqSensorRepository sensorRepository;

  @Autowired private JooqSensorCsvExportQueryRepository exportRepository;

  @Test
  @Transactional
  void should_stream_header_and_rows_matching_the_filter() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          sensorRepository.create(createDummySensor("CSV-01", "UniqueCsvExportName", "x"));
          sensorRepository.create(createDummySensor("CSV-02", "Other Name", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquecsvexport", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(HEADER, lines.getFirst());
          assertEquals(2, lines.size(), "Header plus exactly one matching row");
          assertTrue(
              lines
                  .get(1)
                  .startsWith(
                      "CSV-01,UniqueCsvExportName,SOL_EXPERTS,,,0.0,0.0,0.0,true,,UniqueCsvExportName,"));
        });
  }

  @Test
  @Transactional
  void should_stream_multiple_rows_for_a_multi_parameter_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          Sensor sensor = createDummySensor("CSV-MULTI-01", "UniqueCsvMultiName", "x");
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
                  Map.of("name", new TextFilterModel("contains", "uniquecsvmulti", null)));

          // when
          String csv = streamToString(request);

          // then: header plus one row per parameter, same sensor identity on both
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(3, lines.size(), "Header plus one row per parameter");
          assertTrue(lines.get(1).startsWith("CSV-MULTI-01,UniqueCsvMultiName,"));
          assertTrue(lines.get(2).startsWith("CSV-MULTI-01,UniqueCsvMultiName,"));
          assertTrue(
              List.of(lines.get(1), lines.get(2)).stream()
                  .anyMatch(line -> line.contains("Param One")));
          assertTrue(
              List.of(lines.get(1), lines.get(2)).stream()
                  .anyMatch(line -> line.contains("Param Two")));
        });
  }

  @Test
  @Transactional
  void should_stream_a_single_row_with_blank_parameter_columns_for_a_parameterless_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a sensor created with no parameters at all
          Sensor sensor = createDummySensor("CSV-NONE-01", "UniqueCsvNoneName", "x");
          sensor.setParameters(new ArrayList<>());
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquecsvnone", null)));

          // when
          String csv = streamToString(request);

          // then: the sensor is not dropped by the LEFT JOIN, its one row just has blank
          // parameter columns
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(2, lines.size(), "Header plus exactly one row");
          assertEquals(
              String.join(
                  ",",
                  "CSV-NONE-01",
                  "UniqueCsvNoneName",
                  "SOL_EXPERTS",
                  "", // fulcrumId
                  "", // mainExperiment.name
                  // x/y/z are DOUBLE PRECISION since V15, so the export carries their decimals
                  "0.0",
                  "0.0",
                  "0.0",
                  "true",
                  "", // comment
                  "", // parameter.name
                  "", // parameter.dasParameterAlias
                  "", // parameter.type.name
                  "", // parameter.unit
                  "", // parameter.formula.expression
                  "", // parameter.alarmLimits.lower
                  "", // parameter.alarmLimits.upper
                  "", // parameter.active
                  "" // parameter.comment
                  ),
              lines.get(1));
        });
  }

  @Test
  @Transactional
  void should_filter_by_a_parameter_level_field() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: two sensors, only one has a parameter matching the filter
          Sensor matching = createDummySensor("CSV-PARAM-FILTER-01", "ParamFilterSensorA", "x");
          matching.setParameters(
              new ArrayList<>(
                  List.of(buildParameter("UniqueCsvParamFilterName", "x", 0.0, 100.0))));
          sensorRepository.create(matching);

          Sensor nonMatching = createDummySensor("CSV-PARAM-FILTER-02", "ParamFilterSensorB", "x");
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
                      new TextFilterModel("contains", "uniquecsvparamfilter", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(2, lines.size(), "Header plus exactly one matching row");
          assertTrue(lines.get(1).startsWith("CSV-PARAM-FILTER-01,"));
        });
  }

  @Test
  @Transactional
  void should_honor_sort_model_descending() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          sensorRepository.create(createDummySensor("SORT-CSV-A", "AAA_CSV_SORT", "x"));
          sensorRepository.create(createDummySensor("SORT-CSV-Z", "ZZZ_CSV_SORT", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(new SortModelItem("name", SortDirection.DESC)),
                  Map.of("name", new TextFilterModel("contains", "_CSV_SORT", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(3, lines.size(), "Header plus our two sensors");
          assertTrue(
              lines.get(1).startsWith("SORT-CSV-Z,"),
              "ZZZ_CSV_SORT should sort before AAA_CSV_SORT in DESC order");
          assertTrue(lines.get(2).startsWith("SORT-CSV-A,"));
        });
  }

  @Test
  @Transactional
  void should_cap_rows_at_the_export_requests_limit() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: three sensors matching the filter, but the request's limit only allows 2
          sensorRepository.create(createDummySensor("CAP-01", "CapTest One", "x"));
          sensorRepository.create(createDummySensor("CAP-02", "CapTest Two", "x"));
          sensorRepository.create(createDummySensor("CAP-03", "CapTest Three", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  2,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "CapTest", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(3, lines.size(), "Header plus exactly 2 rows, not all 3 matches");
        });
  }

  @Test
  @Transactional
  void should_escape_a_comment_containing_a_comma_and_a_quote() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          Sensor sensor = createDummySensor("ESCAPE-01", "Escape Test", "x");
          sensor.setComment("Loud, \"noisy\" comment");
          sensorRepository.create(sensor);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "Escape Test", null)));

          // when
          String csv = streamToString(request);

          // then
          assertTrue(
              csv.contains("\"Loud, \"\"noisy\"\" comment\""),
              "Comment with a comma and quotes must be RFC4180-escaped: " + csv);
        });
  }

  private String streamToString(PagedRequest request) {
    StringWriter writer = new StringWriter();
    try {
      exportRepository.streamCsv(request, writer);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
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
    Sensor sensor =
        new Sensor(
            name, code, Das.SOL_EXPERTS, null, null, new Coordinates(0d, 0d, 0d), true, null);
    sensor.setParameters(
        new ArrayList<>(List.of(buildParameter(name, formulaExpression, 0.0, 100.0))));
    return sensor;
  }
}
