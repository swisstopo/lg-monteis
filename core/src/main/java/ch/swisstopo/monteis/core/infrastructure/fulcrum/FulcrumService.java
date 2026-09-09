package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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
 */
@Service
public class FulcrumService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumService.class);

  /** JSON objects per row, which is what {@link FulcrumSensor} is mapped from. */
  private static final String JSON_FORMAT = "json";

  private static final boolean WITH_HEADERS = true;
  // metadata=true would prepend the "fields" column listing to the response, which nothing here
  // reads - FulcrumQueryResponse maps "rows" only.
  private static final boolean WITHOUT_COLUMN_METADATA = false;
  private static final boolean ROWS_AS_OBJECTS = false;
  private static final int FIRST_PAGE = 1;

  /** Identifies MonTEIS in Fulcrum's request logs; the spec's own default is just "Application". */
  private static final String USER_AGENT = "monteis-core";

  private static final TypeReference<FulcrumQueryResponse<FulcrumSensor>> SENSOR_ROWS =
      new TypeReference<>() {};

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final ObjectMapper objectMapper;

  public FulcrumService(
      DefaultApi fulcrumApi, FulcrumProperties properties, ObjectMapper objectMapper) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * Fetches a single record by its Fulcrum record id, i.e. the value MonTEIS stores as
   * {@code Sensor.fulcrumId}.
   *
   * <p>The filter is pushed into the SQL statement instead of paging the whole table and
   * filtering here. The id is a {@link UUID}, so interpolating it into the statement cannot
   * inject anything.
   *
   * @param fulcrumRecordId the record id to look up
   * @return the record, or empty when Fulcrum holds no record with that id
   */
  public Optional<FulcrumSensor> getSensorById(UUID fulcrumRecordId) {
    String sql =
        "SELECT * FROM \"%s\" WHERE _record_id = '%s';"
            .formatted(properties.sensorTable(), fulcrumRecordId);

    List<FulcrumSensor> rows = query(sql);
    if (rows.size() > 1) {
      log.warn(
          "Fulcrum returned {} records for record id {}, using the first one",
          rows.size(),
          fulcrumRecordId);
    }

    return rows.stream().findFirst();
  }

  /**
   * Fetches every record of the systems-and-sensors table, systems, packers and intervals
   * included. Use {@link FulcrumSensor#isSensor()} to keep only the sensor rows.
   */
  public List<FulcrumSensor> getSensors() {
    return query("SELECT * FROM \"%s\";".formatted(properties.sensorTable()));
  }

  private List<FulcrumSensor> query(String sql) {
    log.debug("Querying Fulcrum: {}", sql);

    Object body =
        fulcrumApi
            .queryGet(
                sql,
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

    if (body == null) {
      return List.of();
    }

    // The spec types this response as an empty object (see FulcrumSpecTest), so the generated
    // signature can only promise Object and the row envelope is converted here.
    return objectMapper.convertValue(body, SENSOR_ROWS).rows();
  }
}
