package ch.swisstopo.monteis.core.infrastructure.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestTimingTest {

  @AfterEach
  void tearDown() {
    RequestTiming.end();
  }

  @Test
  void should_accumulate_time_rows_and_query_count_across_statements() {
    RequestTiming.begin();

    RequestTiming.record(5_000_000L, 10);
    RequestTiming.record(7_000_000L, 90);

    RequestTiming timing = RequestTiming.current();
    assertEquals(12, timing.databaseMillis());
    assertEquals(100, timing.rows());
    assertEquals(2, timing.queries());
  }

  @Test
  void should_record_nothing_when_no_request_is_in_scope() {
    // No begin() — this is a startup task or scheduled job, not a request. Must not throw, and
    // must not leave a collector behind on the thread.
    RequestTiming.record(9_000_000L, 500);

    assertNull(RequestTiming.current());
  }

  @Test
  void should_release_the_thread_local_on_end() {
    RequestTiming.begin();
    assertNotNull(RequestTiming.current());

    RequestTiming.end();

    // Matters because request threads are pooled: a leak would report one request's database time
    // against a later, unrelated request on the same thread.
    assertNull(RequestTiming.current());
  }

  @Test
  void should_keep_threads_isolated() throws ExecutionException, InterruptedException {
    RequestTiming.begin();
    RequestTiming.record(10_000_000L, 100);

    // A second "request" on another thread must not see or disturb this one's totals
    long otherThreadRows =
        CompletableFuture.supplyAsync(
                () -> {
                  RequestTiming.begin();
                  RequestTiming.record(1_000_000L, 7);
                  long rows = RequestTiming.current().rows();
                  RequestTiming.end();
                  return rows;
                })
            .get();

    assertEquals(7, otherThreadRows);
    assertEquals(100, RequestTiming.current().rows());
  }

  @Test
  void should_start_from_zero_when_a_thread_is_reused() {
    RequestTiming.begin();
    RequestTiming.record(10_000_000L, 100);
    RequestTiming.end();

    RequestTiming.begin();

    assertEquals(0, RequestTiming.current().rows());
    assertEquals(0, RequestTiming.current().queries());
    assertEquals(0, RequestTiming.current().databaseMillis());
  }
}
