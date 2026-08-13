package ch.swisstopo.monteis.core.infrastructure.observability;

/**
 * Per-request accumulator of database time, bound to the request thread.
 *
 * <p>Exists so a request can be split into "time in the database" and "everything else". Timing
 * inside a controller cannot do that: Jackson serialization and the response write both happen
 * after the controller returns.
 *
 * <p>Only counts work done on the thread that called {@link #begin()}. Anything offloaded to
 * another thread is invisible here, and statements executed outside a request (startup tasks,
 * scheduled jobs) are simply not recorded.
 */
public final class RequestTiming {

  private static final ThreadLocal<RequestTiming> CURRENT = new ThreadLocal<>();

  private long databaseNanos;
  private long rows;
  private int queries;

  private RequestTiming() {}

  /** Starts collecting on this thread, replacing anything already there. */
  static void begin() {
    CURRENT.set(new RequestTiming());
  }

  /** Stops collecting and releases the thread-local. Must be called in a finally block. */
  static void end() {
    CURRENT.remove();
  }

  /** The collector for this thread, or {@code null} when no request is in scope. */
  static RequestTiming current() {
    return CURRENT.get();
  }

  /** Records one statement. A no-op when no request is in scope. */
  static void record(long nanos, long rowsFetched) {
    RequestTiming timing = CURRENT.get();
    if (timing == null) {
      return;
    }
    timing.databaseNanos += nanos;
    timing.rows += rowsFetched;
    timing.queries++;
  }

  long databaseMillis() {
    return databaseNanos / 1_000_000;
  }

  long rows() {
    return rows;
  }

  int queries() {
    return queries;
  }
}
