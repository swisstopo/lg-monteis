package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.exception.TooManyExportRowsException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link JooqMeasurementCsvExportQueryRepository} against the seeded dev dataset (see
 * {@link MeasurementQueryRepositoryIT} for the seeded sensors). Same row grain and filter semantics
 * as the grid's {@link MeasurementQueryRepository#findPaged}.
 */
@IT
class JooqMeasurementCsvExportQueryRepositoryIT {

  private static final String HEADER =
      "Experiment,DAS Key,Sensor Name,Parameter,Sensor Type,Newest Measurement,Value,"
          + "Measurement Status,Unit,Alarm Limit From,Alarm Limit To,Active,X (Local),Y (Local),"
          + "Z (Local),Comment";

  private static final List<UUID> EXPERIMENT_1_ONLY =
      List.of(UUID.fromString("00000000-0000-7000-8000-000000000301"));

  @Autowired private JooqMeasurementCsvExportQueryRepository exportRepository;

  @Test
  @Transactional
  void should_stream_header_and_rows_matching_the_filter() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // when
          String csv = streamToString(dasKeyEquals("SOL_EXPERTS__TEMP-1__temperature"));

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(HEADER, lines.getFirst());
          assertEquals(2, lines.size(), "Header plus exactly one matching row");
          String row = lines.get(1);
          assertTrue(
              row.startsWith(
                  "Mont Terri Alpha,SOL_EXPERTS__TEMP-1__temperature,monteis-001,"
                      + "Temperature Param,Temperature,"),
              row);
          assertTrue(
              row.endsWith(
                  ",K,-50,100,Yes,100,200,300," + "Air temperature sensor near ventilation intake"),
              row);
        });
  }

  @Test
  @Transactional
  void should_exclude_rows_hidden_by_row_level_security() {
    SecurityContextTestSupport.runAsUser(
        EXPERIMENT_1_ONLY,
        () -> {
          // when
          String csv = streamToString(dasKeyEquals("SOL_EXPERTS__FLOW-Admin__flow"));

          // then
          assertEquals(HEADER + "\r\n", csv);
        });
  }

  @Test
  @Transactional
  void should_refuse_an_export_matching_more_rows_than_the_limit() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given
          PagedRequest request = new PagedRequest(0, 2, List.of(), Map.of());
          StringWriter writer = new StringWriter();

          // when / then
          assertThrows(
              TooManyExportRowsException.class, () -> exportRepository.streamCsv(request, writer));
          assertEquals("", writer.toString());
        });
  }

  private static PagedRequest dasKeyEquals(String dasKey) {
    return new PagedRequest(
        0, 10, List.of(), Map.of("dasKey", new TextFilterModel("equals", dasKey, null)));
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
}
