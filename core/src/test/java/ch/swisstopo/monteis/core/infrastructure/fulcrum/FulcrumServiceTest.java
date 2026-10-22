package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import ch.qos.logback.classic.Level;
import ch.swisstopo.monteis.contracts.fulcrum.BadRequestResponse;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import ch.swisstopo.monteis.core.itconfig.LogCapture;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.json.JsonMapper;

class FulcrumServiceTest {

  private static final String BASE_URL = "https://api.fulcrumapp.com/api";

  // Spelled out rather than read off FulcrumService, so narrowing or widening the statement has
  // to be a deliberate edit here too.
  private static final String COLUMNS =
      "_record_id, _title, system_or_component_category_and_type, fx_point_with_offset,"
          + " fy_point_with_offset, fz_point_with_offset";
  private static final String TABLE = "Mont Terri Monitoring Systems and Sensors";
  private static final String LITERATURE_TABLE = "Mont Terri Literature";
  private static final boolean ENABLED = true;
  private static final boolean DISABLED = false;
  private static final UUID SENSOR_RECORD_ID =
      UUID.fromString("29d8aee7-d9c6-4459-ac9d-bbd6e10f6518");

  private static final String SENSOR_ROW =
      """
      {
        "_record_id": "29d8aee7-d9c6-4459-ac9d-bbd6e10f6518",
        "_title": "CL_BCL-05_T",
        "system_or_component_category_and_type": ["Sensor", "Point", "T"],
        "fx_point_with_offset": 2579333.768,
        "fy_point_with_offset": 1247476.687,
        "fz_point_with_offset": 504.74
      }
      """;

  // The same row as Fulcrum would answer it if the statement were widened or the app grew columns:
  // bookkeeping (_record_key, _edited_duration), intermediate coordinate values (bx_start,
  // fx_centerline) and unmapped fields (monteis_id) that the record has to drop.
  private static final String SENSOR_ROW_WITH_EXTRA_COLUMNS =
      """
      {
        "_record_id": "29d8aee7-d9c6-4459-ac9d-bbd6e10f6518",
        "_record_key": null,
        "_title": "CL_BCL-05_T",
        "_edited_duration": 199,
        "system_or_component_category_and_type": ["Sensor", "Point", "T"],
        "monteis_id": null,
        "bx_start": 2579341.349,
        "fx_centerline": 2579333.768,
        "fx_point_with_offset": 2579333.768,
        "fy_point_with_offset": 1247476.687,
        "fz_point_with_offset": 504.74,
        "3d_length": null
      }
      """;

  private static final String SYSTEM_ROW =
      """
      {
        "_record_id": "bef456b1-72da-4f5c-853f-1de1116c2c45",
        "_title": "CL_BCL-05_MMMS",
        "system_or_component_category_and_type": ["System", "MMMS"]
      }
      """;

  private static final String LITERATURE_ROW =
      """
      {
        "_record_id": "221ae592-4527-4442-8294-8dc2c5be1168",
        "number": "2011-02",
        "reference_title": "DS Experiment: BDS-1: Determination of the 3D total and effective stress",
        "author_sequence": "Jahns E.",
        "year": 2011,
        "categories": "2 Mont Terri Technical Notes (TN)",
        "keywords": ["Experiment DS", "RACOS"],
        "document": ["22c52651-0b4b-48ca-bf8e-6594e935da0f"],
        "document_names": ["Jahns 03 2011 - DS Experiment.pdf"]
      }
      """;

  private MockRestServiceServer server;
  private FulcrumService service;

  @BeforeEach
  void setUp() {
    givenFulcrumIs(ENABLED);
  }

  private void givenFulcrumIs(boolean enabled) {
    givenFulcrumIs(enabled, 20000);
  }

  private void givenFulcrumIs(boolean enabled, int perPage) {
    String apiToken = "test-token";
    FulcrumProperties properties =
        new FulcrumProperties(
            enabled,
            BASE_URL,
            apiToken,
            TABLE,
            LITERATURE_TABLE,
            perPage,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            List.of(URI.create("https://attachments.fulcrumapp-eu.com")));

    JsonMapper objectMapper = JsonMapper.builder().build();

    RestClient.Builder builder = RestClient.builder().apply(FulcrumConfig.configure(properties));
    server = MockRestServiceServer.bindTo(builder).build();

    DefaultApi api =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build()
            .createClient(DefaultApi.class);
    service = new FulcrumService(api, properties, objectMapper);
  }

  @Test
  void getSensorById_queries_the_query_api_with_the_record_id_filter() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(FulcrumConfig.API_TOKEN_HEADER, "test-token"))
        .andExpect(
            sqlQuery(
                "SELECT %s FROM \"%s\" WHERE _record_id = '%s';"
                    .formatted(COLUMNS, TABLE, SENSOR_RECORD_ID)))
        .andExpect(queryParam("format", "json"))
        .andExpect(header("User-Agent", "monteis-core"))
        .andExpect(queryParam("headers", "true"))
        .andExpect(queryParam("metadata", "false"))
        .andExpect(queryParam("arrays", "false"))
        .andExpect(queryParam("page", "1"))
        .andExpect(queryParam("per_page", "20000"))
        .andRespond(withSuccess(rows(SENSOR_ROW), MediaType.APPLICATION_JSON));

    Optional<FulcrumSensor> sensor = service.getSensorById(SENSOR_RECORD_ID);

    assertThat(sensor).isPresent();
    server.verify();
  }

  @Test
  void getSensorById_maps_the_row_onto_the_cleaned_up_record() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withSuccess(rows(SENSOR_ROW), MediaType.APPLICATION_JSON));

    FulcrumSensor sensor = service.getSensorById(SENSOR_RECORD_ID).orElseThrow();

    assertThat(sensor.recordId()).isEqualTo(SENSOR_RECORD_ID);
    assertThat(sensor.title()).isEqualTo("CL_BCL-05_T");
    assertThat(sensor.categoryAndType()).containsExactly("Sensor", "Point", "T");
    assertThat(sensor.isSensor()).isTrue();
    assertThat(sensor.xPointWithOffset()).isEqualTo(2579333.768);
    assertThat(sensor.yPointWithOffset()).isEqualTo(1247476.687);
    assertThat(sensor.zPointWithOffset()).isEqualTo(504.74);
  }

  @Test
  void getSensorById_ignores_columns_the_statement_did_not_ask_for() {
    // Fulcrum answers with whatever its app defines, and an operator can widen the table at any
    // time: a column the record does not map must be dropped rather than fail deserialization.
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withSuccess(rows(SENSOR_ROW_WITH_EXTRA_COLUMNS), MediaType.APPLICATION_JSON));

    FulcrumSensor sensor = service.getSensorById(SENSOR_RECORD_ID).orElseThrow();

    assertThat(sensor.recordId()).isEqualTo(SENSOR_RECORD_ID);
    assertThat(sensor.xPointWithOffset()).isEqualTo(2579333.768);
  }

  @Test
  void getSensorById_returns_empty_when_fulcrum_holds_no_such_record() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withSuccess(rows(), MediaType.APPLICATION_JSON));

    assertThat(service.getSensorById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void getSensorById_surfaces_a_rejected_query_with_the_response_body_attached() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"relation does not exist\",\"status\":400}"));

    // The client deliberately does not translate errors: it lets RestClient's exception through
    // with the body intact, so GlobalErrorControllerAdvice can decode the documented envelope.
    assertThatThrownBy(() -> service.getSensorById(SENSOR_RECORD_ID))
        .isInstanceOf(RestClientResponseException.class)
        .satisfies(
            thrown -> {
              RestClientResponseException e = (RestClientResponseException) thrown;
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getResponseBodyAs(BadRequestResponse.class))
                  .extracting(BadRequestResponse::getError)
                  .isEqualTo("relation does not exist");
            });
  }

  @Test
  void getSensorById_fails_without_calling_fulcrum_when_fulcrum_is_switched_off() {
    givenFulcrumIs(DISABLED);

    assertThatThrownBy(() -> service.getSensorById(SENSOR_RECORD_ID))
        .isInstanceOf(FulcrumDisabledException.class);

    server.verify();
  }

  @Test
  void getSensorById_translates_a_rejected_token_into_an_authentication_failure() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withUnauthorizedRequest());

    assertThatThrownBy(() -> service.getSensorById(SENSOR_RECORD_ID))
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("401")
        .hasCauseInstanceOf(RestClientResponseException.class);

    server.verify();
  }

  @Test
  void getSensors_translates_a_forbidden_token_into_an_authentication_failure() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> service.getSensors())
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("403");

    server.verify();
  }

  @Test
  void getSensors_queries_the_whole_table_and_keeps_non_sensor_rows() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andExpect(sqlQuery("SELECT %s FROM \"%s\";".formatted(COLUMNS, TABLE)))
        .andRespond(withSuccess(rows(SENSOR_ROW, SYSTEM_ROW), MediaType.APPLICATION_JSON));

    List<FulcrumSensor> rows = service.getSensors();

    assertThat(rows).hasSize(2);
    assertThat(rows).filteredOn(FulcrumSensor::isSensor).hasSize(1);
    assertThat(rows.get(1).title()).isEqualTo("CL_BCL-05_MMMS");
    server.verify();
  }

  @Test
  void findLiteratureMarkedForMonteis_asks_for_the_marked_rows_with_files_only() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andExpect(
            sqlQuery(
                "SELECT _record_id, number, reference_title, author_sequence, year, categories,"
                    + " keywords, document, document_names FROM \"Mont Terri Literature\" WHERE"
                    + " synchronise_with_monteis = 'yes' AND document IS NOT NULL;"))
        .andRespond(withSuccess(rows(LITERATURE_ROW), MediaType.APPLICATION_JSON));

    List<FulcrumLiteratureRow> rows = service.findLiteratureMarkedForMonteis();

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.recordId())
                  .isEqualTo(UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168"));
              assertThat(row.number()).isEqualTo("2011-02");
              assertThat(row.title()).startsWith("DS Experiment: BDS-1");
              assertThat(row.authors()).isEqualTo("Jahns E.");
              assertThat(row.year()).isEqualTo(2011);
              assertThat(row.category()).isEqualTo("2 Mont Terri Technical Notes (TN)");
              assertThat(row.keywords()).containsExactly("Experiment DS", "RACOS");
              assertThat(row.firstAttachment())
                  .get()
                  .extracting(FulcrumLiteratureRow.FulcrumAttachmentReference::name)
                  .isEqualTo("Jahns 03 2011 - DS Experiment.pdf");
            });
    server.verify();
  }

  @Test
  void findLiteratureMarkedForMonteis_warns_about_a_row_with_several_files() {
    String withTwoFiles =
        LITERATURE_ROW.replace(
            "\"document\": [\"22c52651-0b4b-48ca-bf8e-6594e935da0f\"]",
            "\"document\": [\"22c52651-0b4b-48ca-bf8e-6594e935da0f\","
                + " \"5f0d7a39-4c1e-4b8e-9a51-3c2f1b0e7d64\"]");
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withSuccess(rows(withTwoFiles), MediaType.APPLICATION_JSON));

    try (LogCapture logs = LogCapture.of(FulcrumService.class, Level.WARN)) {
      service.findLiteratureMarkedForMonteis();

      assertThat(logs.messages())
          .singleElement()
          .asString()
          .contains("221ae592-4527-4442-8294-8dc2c5be1168", "only the first one is taken");
    }
  }

  @Test
  void findLiteratureMarkedForMonteis_skips_a_row_it_cannot_read_and_keeps_the_others() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(
            withSuccess(
                rows(
                    """
                    {
                      "_record_id": "5b1f0c35-2a66-4d43-a2fe-1d3ff4b6a8b0",
                      "year": "ca. 2011",
                      "document": ["0e0c3a8e-5f3c-4a4f-9d1e-0c9a1b7f6e21"]
                    }
                    """,
                    LITERATURE_ROW),
                MediaType.APPLICATION_JSON));

    List<FulcrumLiteratureRow> rows = service.findLiteratureMarkedForMonteis();

    assertThat(rows)
        .singleElement()
        .extracting(FulcrumLiteratureRow::recordId)
        .isEqualTo(UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168"));
  }

  @Test
  void a_query_fails_when_fulcrum_fills_the_whole_page() {
    // only first page fetched, missing rows would look deleted
    givenFulcrumIs(ENABLED, 2);
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andRespond(withSuccess(rows(LITERATURE_ROW, LITERATURE_ROW), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.findLiteratureMarkedForMonteis())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monteis.fulcrum.per-page");
  }

  private static RequestMatcher sqlQuery(String expectedSql) {
    return request -> {
      String rawSql =
          UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("q");
      assertThat(rawSql).isNotNull();
      assertThat(UriUtils.decode(rawSql, StandardCharsets.UTF_8)).isEqualTo(expectedSql);
    };
  }

  private static String rows(String... rows) {
    return """
    {"fields": [{"name": "_record_id", "type": "string"}], "rows": [%s], "time": 0.001,
     "date": 1788945317832}
    """
        .formatted(String.join(",", rows));
  }
}
