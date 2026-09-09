package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.swisstopo.monteis.contracts.fulcrum.BadRequestResponse;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.json.JsonMapper;

class FulcrumServiceTest {

  private static final String BASE_URL = "https://api.fulcrumapp.com/api";
  private static final String TABLE = "Mont Terri Monitoring Systems and Sensors";
  private static final UUID SENSOR_RECORD_ID =
      UUID.fromString("29d8aee7-d9c6-4459-ac9d-bbd6e10f6518");

  // Trimmed to the mapped columns plus a few unmapped ones (_record_key, _edited_duration,
  // bx_start, 3d_length), so the test also covers that Fulcrum's remaining ~70 columns are
  // ignored instead of failing deserialization.
  private static final String SENSOR_ROW =
      """
      {
        "_record_id": "29d8aee7-d9c6-4459-ac9d-bbd6e10f6518",
        "_record_key": null,
        "_status": "In progress",
        "_latitude": null,
        "_longitude": null,
        "_created_at": "2026-09-01T09:16:55.000Z",
        "_updated_at": "2026-09-01T09:16:55.000Z",
        "_version": 1,
        "_edited_duration": 199,
        "_title": "CL_BCL-05_T",
        "system_or_component_category_and_type": ["Sensor", "Point", "T"],
        "occurrence": "system",
        "system_link": ["bef456b1-72da-4f5c-853f-1de1116c2c45"],
        "automatic_name": "CL_BCL-05_T",
        "sensor_id": "solexpert_ID",
        "historic_name": "T1",
        "experiment": ["a51f2e45-23e2-469c-a214-05145c926ec0"],
        "installed_date": "2024-02-25T00:00:00.000Z",
        "related_to_borehole": ["6aa5e449-588c-4383-af7c-9ef9aba6ad0d"],
        "experiment_abbreviation": "CL",
        "experiment_id": "a51f2e45-23e2-469c-a214-05145c926ec0",
        "borehole_name": "BCL-05",
        "borehole_bim_id": "48596",
        "monteis_id": null,
        "system_manufacturer": "Swisstopo ",
        "sensor_inside_or_outside_borehole": "Inside borehole",
        "borehole_packer_or_interval_state_recording": "Interval",
        "select_borehole_packer_or_interval": ["2d4a370b-343f-47ff-bd6e-7d6c8d2f1855"],
        "connected_to_data_acquisition_system": ["6ea113d2-fed9-4a20-b407-031896a8566c"],
        "bim_id_of_data_acquisition_system": "36408",
        "start_distance_in_m": 6.8,
        "end_distance_in_m": 7,
        "calculated_length_in_m": 0.2,
        "reference_for_depth_calculation": "Borehole Top",
        "installed_depth_from_reference_in_m": 10,
        "bx_start": 2579341.349,
        "fx_centerline": 2579333.768,
        "fy_centerline": 1247476.687,
        "fz_centerline": 504.74,
        "fdz_point_offset": 0,
        "3d_length": null
      }
      """;

  private static final String SYSTEM_ROW =
      """
      {
        "_record_id": "bef456b1-72da-4f5c-853f-1de1116c2c45",
        "_title": "CL_BCL-05_MMMS",
        "system_or_component_category_and_type": ["System", "MMMS"],
        "automatic_name": "CL_BCL-05_MMMS",
        "sensor_id": null,
        "historic_name": "MMMS-1"
      }
      """;

  private MockRestServiceServer server;
  private FulcrumService service;

  @BeforeEach
  void setUp() {
    FulcrumProperties properties =
        new FulcrumProperties(
            BASE_URL, "test-token", TABLE, 20000, Duration.ofSeconds(5), Duration.ofSeconds(60));

    JsonMapper objectMapper = JsonMapper.builder().build();

    // Mirrors FulcrumConfig, minus the request factory it installs - MockRestServiceServer needs
    // to keep its own. The API proxy is the real one.
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(FulcrumConfig.API_TOKEN_HEADER, properties.apiToken());
    server = MockRestServiceServer.bindTo(builder).build();

    DefaultApi api = new FulcrumConfig().fulcrumApi(builder.build());
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
                "SELECT * FROM \"%s\" WHERE _record_id = '%s';".formatted(TABLE, SENSOR_RECORD_ID)))
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
    assertThat(sensor.status()).isEqualTo("In progress");
    assertThat(sensor.version()).isEqualTo(1);
    assertThat(sensor.createdAt()).isEqualTo(Instant.parse("2026-09-01T09:16:55Z"));
    assertThat(sensor.categoryAndType()).containsExactly("Sensor", "Point", "T");
    assertThat(sensor.isSensor()).isTrue();
    assertThat(sensor.sensorId()).isEqualTo("solexpert_ID");
    assertThat(sensor.historicName()).isEqualTo("T1");
    assertThat(sensor.systemLink())
        .containsExactly(UUID.fromString("bef456b1-72da-4f5c-853f-1de1116c2c45"));
    assertThat(sensor.experimentId())
        .isEqualTo(UUID.fromString("a51f2e45-23e2-469c-a214-05145c926ec0"));
    assertThat(sensor.experimentAbbreviation()).isEqualTo("CL");
    assertThat(sensor.boreholeName()).isEqualTo("BCL-05");
    assertThat(sensor.installedDate()).isEqualTo(Instant.parse("2024-02-25T00:00:00Z"));
    assertThat(sensor.stateRecording()).isEqualTo("Interval");
    assertThat(sensor.dataAcquisitionSystemBimId()).isEqualTo("36408");
    assertThat(sensor.startDistanceInM()).isEqualTo(6.8);
    assertThat(sensor.calculatedLengthInM()).isEqualTo(0.2);
    assertThat(sensor.xCenterline()).isEqualTo(2579333.768);
    assertThat(sensor.zCenterline()).isEqualTo(504.74);

    // Sent as null by Fulcrum rather than omitted, and unmapped columns must not leak in.
    assertThat(sensor.latitude()).isNull();
    assertThat(sensor.monteisId()).isNull();
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
  void getSensors_queries_the_whole_table_and_keeps_non_sensor_rows() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/query?")))
        .andExpect(sqlQuery("SELECT * FROM \"%s\";".formatted(TABLE)))
        .andRespond(withSuccess(rows(SENSOR_ROW, SYSTEM_ROW), MediaType.APPLICATION_JSON));

    List<FulcrumSensor> rows = service.getSensors();

    assertThat(rows).hasSize(2);
    assertThat(rows).filteredOn(FulcrumSensor::isSensor).hasSize(1);
    assertThat(rows.get(1).title()).isEqualTo("CL_BCL-05_MMMS");
    server.verify();
  }

  /**
   * Matches the SQL sent as the {@code q} parameter. Spring's own {@code queryParam} matcher
   * compares the raw, percent-encoded value, which would make the expectation unreadable.
   */
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
