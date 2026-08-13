package ch.swisstopo.monteis.core.infrastructure.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.jooq.ExecuteContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueryTimingListenerTest {

  private final ExecuteContext ctx = mock(ExecuteContext.class);

  @AfterEach
  void tearDown() {
    RequestTiming.end();
  }

  /** Drives one statement through the listener's lifecycle. */
  private void execute(QueryTimingListener listener, int rows) {
    listener.start(ctx);
    for (int i = 0; i < rows; i++) {
      listener.recordEnd(ctx);
    }
    listener.end(ctx);
  }

  @Test
  void should_record_row_count_into_the_current_request() {
    RequestTiming.begin();

    execute(new QueryTimingListener(), 3);

    assertEquals(3, RequestTiming.current().rows());
    assertEquals(1, RequestTiming.current().queries());
  }

  @Test
  void should_count_each_statement_separately() {
    RequestTiming.begin();

    // A fresh listener per execution, which is how ObservabilityConfig provides them
    execute(new QueryTimingListener(), 1);
    execute(new QueryTimingListener(), 41);

    assertEquals(42, RequestTiming.current().rows());
    assertEquals(2, RequestTiming.current().queries());
  }

  @Test
  void should_reset_its_row_counter_between_executions_of_the_same_instance() {
    RequestTiming.begin();
    QueryTimingListener listener = new QueryTimingListener();

    execute(listener, 5);
    execute(listener, 2);

    // 7, not 12 — the second execution must not inherit the first execution's count
    assertEquals(7, RequestTiming.current().rows());
  }

  @Test
  void should_measure_elapsed_time_including_the_fetch() {
    RequestTiming.begin();
    QueryTimingListener listener = new QueryTimingListener();

    listener.start(ctx);
    sleepAtLeast(5);
    listener.recordEnd(ctx);
    listener.end(ctx);

    // The FDW streams rows during the fetch, so time spent between start and end must be counted,
    // not just statement execution
    assertTrue(
        RequestTiming.current().databaseMillis() >= 5,
        "expected >=5ms, got " + RequestTiming.current().databaseMillis());
  }

  @Test
  void should_not_fail_when_no_request_is_in_scope() {
    // Startup tasks and scheduled jobs run without a request; the listener must tolerate it
    execute(new QueryTimingListener(), 2);
  }

  private static void sleepAtLeast(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
