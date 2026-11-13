package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.QueryRequest;
import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only access to the sensor metadata (MON-142) and the literature (MON-228) Fulcrum holds.
 *
 * <p>Fulcrum exposes no per-record REST resource for an app's data; the
 * <a href="https://docs.fulcrumapp.com/reference/query-get">Query API</a> is the documented way
 * to read it, taking SQL over the app's tables. The request itself goes through {@link DefaultApi},
 * generated from the Fulcrum spec, so this class only decides what to ask for and what the answer
 * means.
 *
 * <p>The SELECT statements below are therefore request payload, not a query this application runs:
 * they travel as the {@code q} field of an HTTP POST body and are executed by Fulcrum against its own
 * storage. Nothing here touches a Monteis database or a JDBC connection.
 */
@Service
public class FulcrumService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumService.class);
  private static final int FIRST_PAGE = 1;

  private static final TypeReference<FulcrumQueryResponse<FulcrumSensor>> SENSOR_ROWS =
      new TypeReference<>() {};
  private static final TypeReference<FulcrumQueryResponse<JsonNode>> RAW_ROWS =
      new TypeReference<>() {};
  private static final String RECORD_ID = "_record_id";

  /**
   * The columns {@link FulcrumSensor} is built from. Fulcrum's table carries roughly 120 of them,
   * most of its own bookkeeping, so they are listed rather than asked for with {@code *}: the
   * response stays small, and a column disappearing upstream shows up as a rejected statement
   * instead of a silently null field.
   */
  private static final String SENSOR_COLUMNS =
      String.join(
          ", ",
          RECORD_ID,
          "_title",
          "system_or_component_category_and_type",
          "fx_point_with_offset",
          "fy_point_with_offset",
          "fz_point_with_offset");

  private static final String LITERATURE_COLUMNS =
      String.join(
          ", ",
          RECORD_ID,
          "number",
          "reference_title",
          "author_sequence",
          "year",
          "categories",
          "keywords",
          "document",
          "document_names");

  private final DefaultApi fulcrumApi;
  private final FulcrumProperties properties;
  private final ObjectMapper objectMapper;

  public FulcrumService(
      DefaultApi fulcrumApi, FulcrumProperties properties, ObjectMapper objectMapper) {
    this.fulcrumApi = fulcrumApi;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public Optional<FulcrumSensor> findSensorByFulcrumId(UUID fulcrumId) {
    List<FulcrumSensor> rows =
        query(
            "SELECT %s FROM \"%s\" WHERE _record_id = '%s';"
                .formatted(SENSOR_COLUMNS, properties.sensorTable(), fulcrumId),
            SENSOR_ROWS);
    return Optional.ofNullable(firstPerFulcrumId(rows).get(fulcrumId));
  }

  public Map<UUID, FulcrumSensor> findSensorsByFulcrumId(Set<UUID> fulcrumIds) {
    if (fulcrumIds.isEmpty()) {
      return Map.of();
    }
    return firstPerFulcrumId(
        query(
            "SELECT %s FROM \"%s\" WHERE _record_id IN (%s);"
                .formatted(SENSOR_COLUMNS, properties.sensorTable(), quotedListOf(fulcrumIds)),
            SENSOR_ROWS));
  }

  // UUID = hex and dashes only, safe to inline
  private static String quotedListOf(Set<UUID> fulcrumIds) {
    return fulcrumIds.stream().map("'%s'"::formatted).collect(Collectors.joining(", "));
  }

  // one Fulcrum ID per record expected, more = data error in Fulcrum
  private static Map<UUID, FulcrumSensor> firstPerFulcrumId(List<FulcrumSensor> rows) {
    Map<UUID, List<FulcrumSensor>> rowsByFulcrumId =
        rows.stream()
            .collect(
                Collectors.groupingBy(
                    FulcrumSensor::fulcrumId, LinkedHashMap::new, Collectors.toList()));
    rowsByFulcrumId.forEach(FulcrumService::warnAboutDuplicates);
    return rowsByFulcrumId.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));
  }

  private static void warnAboutDuplicates(UUID fulcrumId, List<FulcrumSensor> rows) {
    if (rows.size() > 1) {
      log.warn(
          "Fulcrum holds {} sensor records with Fulcrum ID {}, the first one counts",
          rows.size(),
          fulcrumId);
    }
  }

  // rows without file useless in overview (PO). malformed rows skipped, one must not block rest
  public List<FulcrumLiteratureRow> findLiteratureMarkedForMonteis() {
    List<JsonNode> rows =
        query(
            "SELECT %s FROM \"%s\" WHERE synchronise_with_monteis = 'yes' AND document IS NOT NULL;"
                .formatted(LITERATURE_COLUMNS, properties.literatureTable()),
            RAW_ROWS);
    List<FulcrumLiteratureRow> literature =
        rows.stream().flatMap(row -> literatureRowOf(row).stream()).toList();
    literature.stream()
        .filter(FulcrumLiteratureRow::hasSeveralAttachments)
        .forEach(FulcrumService::warnAboutSeveralAttachments);
    return literature;
  }

  // one file per document expected (PO), more = data error in Fulcrum
  private static void warnAboutSeveralAttachments(FulcrumLiteratureRow row) {
    log.warn(
        "Fulcrum Document {} has the files {}, only the first one is taken",
        row.recordId(),
        row.attachmentIds());
  }

  private Optional<FulcrumLiteratureRow> literatureRowOf(JsonNode row) {
    try {
      return Optional.of(objectMapper.treeToValue(row, FulcrumLiteratureRow.class));
    } catch (JacksonException e) {
      log.warn(
          "Skipping Fulcrum literature row {}, it does not fit FulcrumLiteratureRow: {}",
          row.path(RECORD_ID).asString(),
          e.getOriginalMessage());
      return Optional.empty();
    }
  }

  private <T> List<T> query(String statement, TypeReference<FulcrumQueryResponse<T>> rowType) {
    log.debug("Querying Fulcrum: {}", statement);

    Object body = queryApi(statement);

    if (body == null) {
      return List.of();
    }

    // The spec types this response as an empty object (see FulcrumSpecTest), so the generated
    // signature can only promise Object and the row envelope is converted here.
    List<T> rows = objectMapper.convertValue(body, rowType).rows();
    // only first page fetched, full page => missing rows would look deleted
    if (rows.size() >= properties.perPage()) {
      throw new IllegalStateException(
          "Fulcrum returned a full page of %d rows, raise monteis.fulcrum.per-page"
              .formatted(rows.size()));
    }
    return rows;
  }

  // POST, not GET: Fulcrum caps the url at ~12-16 KB (~300 ids), a refresh sends up to 1000 ids
  private Object queryApi(String statement) {
    try {
      return fulcrumApi
          .queryPost(
              FIRST_PAGE,
              properties.perPage(),
              MediaType.APPLICATION_JSON_VALUE,
              new QueryRequest(statement).format(QueryRequest.FormatEnum.JSON))
          .getBody();
    } catch (RestClientResponseException e) {
      throw FulcrumAuthenticationException.translateRejectedToken(e);
    }
  }
}
