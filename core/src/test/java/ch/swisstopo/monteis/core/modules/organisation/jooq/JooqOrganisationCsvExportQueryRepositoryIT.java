 package ch.swisstopo.monteis.core.modules.organisation.jooq;

 import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.assertj.core.api.Assertions.assertThatThrownBy;

 import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
 import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
 import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
 import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
 import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
 import ch.swisstopo.monteis.core.itconfig.IT;
 import java.io.IOException;
 import java.io.StringWriter;
 import java.io.UncheckedIOException;
 import java.util.List;
 import java.util.Map;
 import java.util.UUID;
 import org.jooq.DSLContext;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.transaction.annotation.Transactional;

 @IT
 @Transactional
 class JooqOrganisationCsvExportQueryRepositoryIT {

  private static final String HEADER = "name,id";

  @Autowired private DSLContext dsl;
  @Autowired private JooqOrganisationCsvExportQueryRepository exportRepository;

  @Test
  void should_stream_header_and_rows_matching_the_filter() {
    UUID id = createOrganisation("UniqueCsvExportOrganisation");
    createOrganisation("Other Organisation");

    List<String> lines =
        lines(
            new PagedRequest(
                0,
                10,
                List.of(),
                Map.of("name", new TextFilterModel("contains", "uniquecsvexport", null))));

    assertThat(lines).containsExactly(HEADER, "UniqueCsvExportOrganisation," + id);
  }

  @Test
  void should_sort_by_name_when_the_request_has_no_sort_model() {
    createOrganisation("Csv default sort B");
    createOrganisation("Csv default sort A");

    List<String> lines = lines(filterByName("Csv default sort", List.of(), 10));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).startsWith("Csv default sort A,");
    assertThat(lines.get(2)).startsWith("Csv default sort B,");
  }

  @Test
  void should_honor_the_requested_sort() {
    createOrganisation("Csv sorted A");
    createOrganisation("Csv sorted B");

    List<String> lines =
        lines(
            filterByName(
                "Csv sorted", List.of(new SortModelItem("name", SortDirection.DESC)), 10));

    assertThat(lines.get(1)).startsWith("Csv sorted B,");
    assertThat(lines.get(2)).startsWith("Csv sorted A,");
  }

  @Test
  void should_quote_names_with_special_characters() {
    UUID id = createOrganisation("Csv, \"quoted\" Org");

    List<String> lines = lines(filterByName("Csv, ", List.of(), 10));

    assertThat(lines).contains("\"Csv, \"\"quoted\"\" Org\"," + id);
  }

  @Test
  void should_cap_rows_at_the_export_requests_limit() {
    createOrganisation("Csv cap One");
    createOrganisation("Csv cap Two");
    createOrganisation("Csv cap Three");

    List<String> lines = lines(filterByName("Csv cap", List.of(), 2));

    assertThat(lines).hasSize(3);
  }

  @Test
  void should_write_only_the_header_when_nothing_matches() {
    List<String> lines = lines(filterByName("no such organisation anywhere", List.of(), 10));

    assertThat(lines).containsExactly(HEADER);
  }

  @Test
  void should_reject_an_unknown_filter_column_before_writing_anything() {
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("bogus", new TextFilterModel("contains", "x", null)));
    StringWriter writer = new StringWriter();

    assertThatThrownBy(() -> exportRepository.streamCsv(request, writer))
        .isInstanceOf(InvalidPagedRequestException.class);
    assertThat(writer.toString()).isEmpty();
  }

  private PagedRequest filterByName(String contains, List<SortModelItem> sort, int limit) {
    return new PagedRequest(
        0, limit, sort, Map.of("name", new TextFilterModel("contains", contains, null)));
  }

  private List<String> lines(PagedRequest request) {
    StringWriter writer = new StringWriter();
    try {
      exportRepository.streamCsv(request, writer);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return List.of(writer.toString().split("\r\n"));
  }

  private UUID createOrganisation(String name) {
    return dsl.insertInto(ORGANISATIONS)
        .set(ORGANISATIONS.NAME, name)
        .returning(ORGANISATIONS.ID)
        .fetchSingle()
        .getId();
  }
 }
