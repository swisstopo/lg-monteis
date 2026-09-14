package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 */
@IT
class JooqSensorCsvExportQueryRepositoryIT {
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
          assertEquals(
              "dasSensorAlias,name,das,fulcrumId,mainExperiment.name,coordinates.x,coordinates.y,"
                  + "coordinates.z,active,comment",
              lines.getFirst());
          assertEquals(2, lines.size(), "Header plus exactly one matching row");
          assertTrue(lines.get(1).startsWith("CSV-01,UniqueCsvExportName,"));
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
        new Sensor(name, code, DAS.SOL_EXPERTS, null, null, new Coordinates(0, 0, 0), true, null);
    sensor.setParameters(new ArrayList<>(List.of(parameter)));
    return sensor;
  }
}
