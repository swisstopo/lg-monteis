package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only access to the sensor metadata Fulcrum holds (MON-142).
 *
 * <p>Fulcrum exposes no per-record REST resource for an app's data; the
 * <a href="https://docs.fulcrumapp.com/reference/query-get">Query API</a> is the documented way
 * to read it, taking SQL over the app's tables. The request itself goes through {@link DefaultApi},
 * generated from the Fulcrum spec, so this class only decides what to ask for and what the answer
 * means.
 *
 * <p>The SELECT statements below are therefore request payload, not a query this application runs:
 * they travel as the {@code q} parameter of an HTTP GET and are executed by Fulcrum against its own
 * storage. Nothing here touches a Monteis database or a JDBC connection.
 */
@Service
public class FulcrumService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumService.class);
  private static final String JSON_FORMAT = "json";

  private static final boolean WITH_HEADERS = true;
  private static final boolean WITHOUT_COLUMN_METADATA = false;
  private static final boolean ROWS_AS_OBJECTS = false;
  private static final int FIRST_PAGE = 1;

  private static final String USER_AGENT = "monteis-core";

  private static final TypeReference<FulcrumQueryResponse<FulcrumSensor>> SENSOR_ROWS =
      new TypeReference<>() {};

  /**
   * The columns {@link FulcrumSensor} is built from. Fulcrum's table carries roughly 120 of them,
   * most of its own bookkeeping, so they are listed rather than asked for with {@code *}: the
   * response stays small, and a column disappearing upstream shows up as a rejected statement
   * instead of a silently null field.
   */
  private static final String SENSOR_COLUMNS =
      String.join(
          ", ",
          "_record_id",
          "_title",
          "system_or_component_category_and_type",
          "fx_point_with_offset",
          "fy_point_with_offset",
          "fz_point_with_offset");

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final ObjectMapper objectMapper;

  public FulcrumService(
      DefaultApi fulcrumApi, FulcrumProperties properties, ObjectMapper objectMapper) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public Optional<FulcrumSensor> getSensorById(UUID fulcrumRecordId) {
    String statement =
        "SELECT %s FROM \"%s\" WHERE _record_id = '%s';"
            .formatted(SENSOR_COLUMNS, properties.sensorTable(), fulcrumRecordId);

    List<FulcrumSensor> rows = query(statement);
    if (rows.size() > 1) {
      log.warn(
          "Fulcrum returned {} records for record id {}, using the first one",
          rows.size(),
          fulcrumRecordId);
    }

    return rows.stream().findFirst();
  }

  public List<FulcrumSensor> getSensors() {
    return query("SELECT %s FROM \"%s\";".formatted(SENSOR_COLUMNS, properties.sensorTable()));
  }

  private List<FulcrumSensor> query(String statement) {
    log.debug("Querying Fulcrum: {}", statement);

    if (properties.apiToken() == null || properties.apiToken().isBlank()) {
      throw new FulcrumAuthenticationException(
          "No Fulcrum API token configured (monteis.fulcrum.api-token)");
    }

    Object body = queryApi(statement);

    if (body == null) {
      return List.of();
    }

    // The spec types this response as an empty object (see FulcrumSpecTest), so the generated
    // signature can only promise Object and the row envelope is converted here.
    return objectMapper.convertValue(body, SENSOR_ROWS).rows();
  }

  private Object queryApi(String statement) {
    try {
      return fulcrumApi
          .queryGet(
              statement,
              JSON_FORMAT,
              WITH_HEADERS,
              WITHOUT_COLUMN_METADATA,
              ROWS_AS_OBJECTS,
              null, // table_name applies to the postgres format only
              null, // sorting is expressed in the statement itself
              null,
              FIRST_PAGE,
              properties.perPage(),
              MediaType.APPLICATION_JSON_VALUE,
              USER_AGENT)
          .getBody();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED
          || e.getStatusCode() == HttpStatus.FORBIDDEN) {
        throw new FulcrumAuthenticationException(
            "Fulcrum rejected the configured API token with %s".formatted(e.getStatusCode()), e);
      }
      throw e;
    }
  }
}
