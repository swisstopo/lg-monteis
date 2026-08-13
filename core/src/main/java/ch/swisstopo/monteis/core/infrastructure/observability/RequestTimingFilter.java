package ch.swisstopo.monteis.core.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one line per request: total wall clock, how much of it was database time, and how much was
 * everything else.
 *
 * <p>The filter sits outside Spring MVC, so unlike timing inside a controller the total also covers
 * Jackson serialization and the response write. {@code other} is therefore a direct read on how
 * much of a slow request is serialization rather than query time.
 *
 * <p>Cheap enough for production: one thread-local and one {@code nanoTime} per request, plus one
 * {@code nanoTime} per statement. When the feature is switched off the filter is not registered at
 * all, so it costs nothing.
 *
 * <p>Response size is deliberately not measured — counting bytes needs a response wrapper around
 * the output stream, and Tomcat's access log already provides it via {@code server.tomcat.accesslog}.
 */
public class RequestTimingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    RequestTiming.begin();
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // In a finally block so a failed request is still reported and, more importantly, so the
      // thread-local is always released back to the pooled request thread.
      try {
        logSummary(request, response, System.nanoTime() - start);
      } finally {
        RequestTiming.end();
      }
    }
  }

  private void logSummary(HttpServletRequest request, HttpServletResponse response, long nanos) {
    if (!log.isInfoEnabled()) {
      return;
    }
    long totalMillis = nanos / 1_000_000;
    RequestTiming timing = RequestTiming.current();
    long databaseMillis = timing == null ? 0 : timing.databaseMillis();

    log.info(
        "{} {}{} -> {} | total={}ms db={}ms other={}ms | queries={} rows={}",
        request.getMethod(),
        request.getRequestURI(),
        request.getQueryString() == null ? "" : "?" + request.getQueryString(),
        response.getStatus(),
        totalMillis,
        databaseMillis,
        totalMillis - databaseMillis,
        timing == null ? 0 : timing.queries(),
        timing == null ? 0 : timing.rows());
  }
}
