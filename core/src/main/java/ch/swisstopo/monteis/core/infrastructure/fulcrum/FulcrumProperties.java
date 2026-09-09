package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Fulcrum REST integration (MON-142).
 *
 * <p>Fulcrum is the system of record for sensor metadata; MonTEIS reads it through the
 * <a href="https://docs.fulcrumapp.com/reference/query-get">Query API</a>, a read-only SQL
 * endpoint over the Fulcrum app's tables.
 *
 * @param baseUrl root of the Fulcrum API, i.e. everything before {@code /v2/query}. Regional
 *     deployments have their own host (US/AU/CA/EU), all four listed as servers in the spec.
 * @param apiToken value of the {@code X-ApiToken} header. Empty by default so the application
 *     still starts without a token configured; requests then fail with 401 instead of at startup.
 * @param sensorTable name of the Fulcrum table holding the systems and sensors records. Quoted
 *     into the SQL statement, so it may contain spaces.
 * @param perPage page size sent to the Query API. Fulcrum's own default and maximum is 20000.
 * @param connectTimeout TCP connect timeout for a Query API call.
 * @param readTimeout socket read timeout for a Query API call. Generous, because a full table
 *     scan of the sensors table is a slow query on Fulcrum's side.
 */
@ConfigurationProperties("monteis.fulcrum")
public record FulcrumProperties(
    @DefaultValue("https://api.fulcrumapp.com/api") String baseUrl,
    @DefaultValue("") String apiToken,
    @DefaultValue("Mont Terri Monitoring Systems and Sensors") String sensorTable,
    @DefaultValue("20000") int perPage,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout) {}
