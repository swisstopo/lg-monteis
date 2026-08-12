package ch.swisstopo.monteis.core.infrastructure.observability;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the timing instrumentation against the real application context and a real database.
 *
 * <p>The unit tests drive the listener directly; what they cannot prove is that Spring Boot's jOOQ
 * auto-configuration actually picks up the {@code ExecuteListenerProvider} bean and attaches it to
 * the {@code DSLContext} the application uses. That wiring is the part most likely to break on a
 * Spring Boot upgrade, and it fails silently — timings would simply read zero.
 *
 * <p>Requires {@code monteis.observability.request-timing.enabled=true}, set in the test profile.
 */
@IT
class ObservabilityIT {

  @Autowired private DSLContext dsl;

  @AfterEach
  void tearDown() {
    RequestTiming.end();
  }

  @Test
  @Transactional
  void should_record_real_queries_into_the_current_request() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          RequestTiming.begin();

          dsl.select(SENSORS.CODE).from(SENSORS).fetch();
          dsl.select(SENSORS.CODE).from(SENSORS).limit(1).fetch();

          RequestTiming timing = RequestTiming.current();
          assertEquals(
              2,
              timing.queries(),
              "the ExecuteListenerProvider bean is not attached to the application's DSLContext");
          assertTrue(timing.rows() > 0, "expected fetched rows to be counted");
        });
  }

  @Test
  @Transactional
  void should_not_record_anything_outside_a_request() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // No RequestTiming.begin() — mirrors a scheduled job or startup task
          dsl.select(SENSORS.CODE).from(SENSORS).limit(1).fetch();

          assertNull(RequestTiming.current());
        });
  }
}
