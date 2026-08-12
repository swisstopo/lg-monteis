package ch.swisstopo.monteis.core.infrastructure.observability;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

/**
 * Feeds each jOOQ statement's duration and row count into {@link RequestTiming}, so {@link
 * RequestTimingFilter} can report how much of a request was database time.
 *
 * <p>Logs nothing itself. Statement logging is jOOQ's own {@code LoggerListener}, enabled with
 * {@code logging.level.org.jooq.tools.LoggerListener=DEBUG} — note it also renders a preview of the
 * result set, which is expensive enough on a large fetch to distort the timings measured here, so
 * turn it off when measuring.
 *
 * <p>{@code start}/{@code end} bracket the whole statement lifecycle, so the time includes fetching
 * every row. That matters here: the FDW streams rows from TimescaleDB during the fetch rather than
 * at execute time, so measuring only execution would miss most of the cost.
 *
 * <p>Instances hold per-statement state, so a fresh one is created per execution — see {@link
 * ObservabilityConfig}.
 */
public class QueryTimingListener implements ExecuteListener {

  private long startNanos;
  private long rows;

  @Override
  public void start(ExecuteContext ctx) {
    startNanos = System.nanoTime();
    rows = 0;
  }

  @Override
  public void recordEnd(ExecuteContext ctx) {
    rows++;
  }

  @Override
  public void end(ExecuteContext ctx) {
    RequestTiming.record(System.nanoTime() - startNanos, rows);
  }
}
