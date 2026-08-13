package ch.swisstopo.monteis.core.infrastructure.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTimingFilterTest {

  private final RequestTimingFilter filter = new RequestTimingFilter();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @AfterEach
  void tearDown() {
    RequestTiming.end();
  }

  private MockHttpServletRequest request(String uri, String query) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setQueryString(query);
    return request;
  }

  @Test
  void should_log_a_summary_with_the_database_split() throws Exception {
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            // Simulate two statements taking 30ms in total
            RequestTiming.record(20_000_000L, 500);
            RequestTiming.record(10_000_000L, 1);
          }
        };

    try (LogCapture logs = LogCapture.of(RequestTimingFilter.class, Level.INFO)) {
      filter.doFilter(request("/api/measurements/charts/data", "id=1"), response, chain);

      String line = logs.messages().getFirst();
      assertTrue(line.contains("GET /api/measurements/charts/data?id=1"), line);
      assertTrue(line.contains("-> 200"), line);
      assertTrue(line.contains("db=30ms"), line);
      assertTrue(line.contains("queries=2"), line);
      assertTrue(line.contains("rows=501"), line);
    }
  }

  @Test
  void should_omit_the_query_string_when_there_is_none() throws Exception {
    try (LogCapture logs = LogCapture.of(RequestTimingFilter.class, Level.INFO)) {
      filter.doFilter(request("/api/overview/metrics", null), response, new MockFilterChain());

      assertTrue(logs.messages().getFirst().contains("GET /api/overview/metrics ->"));
    }
  }

  @Test
  void should_report_the_actual_status_code() throws Exception {
    response.setStatus(404);

    try (LogCapture logs = LogCapture.of(RequestTimingFilter.class, Level.INFO)) {
      filter.doFilter(
          request("/api/measurements/charts/data", "id=9"), response, new MockFilterChain());

      assertTrue(logs.messages().getFirst().contains("-> 404"));
    }
  }

  @Test
  void should_release_the_thread_local_after_a_successful_request() throws Exception {
    filter.doFilter(request("/api/overview/metrics", null), response, new MockFilterChain());

    assertNull(RequestTiming.current());
  }

  @Test
  void should_release_the_thread_local_when_the_request_fails() {
    MockFilterChain failing =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws IOException {
            throw new IOException("boom");
          }
        };

    assertThrows(
        IOException.class,
        () -> filter.doFilter(request("/api/overview/metrics", null), response, failing));

    // Request threads are pooled — a leak here would misattribute timings to the next request
    assertNull(RequestTiming.current());
  }

  @Test
  void should_still_log_when_the_request_fails() {
    MockFilterChain failing =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws ServletException {
            RequestTiming.record(5_000_000L, 3);
            throw new ServletException("boom");
          }
        };

    try (LogCapture logs = LogCapture.of(RequestTimingFilter.class, Level.INFO)) {
      assertThrows(
          ServletException.class,
          () -> filter.doFilter(request("/api/overview/metrics", null), response, failing));

      // A failed request is exactly when the timing breakdown is most interesting
      assertEquals(1, logs.messages().size());
      assertTrue(logs.messages().getFirst().contains("db=5ms"));
    }
  }

  @Test
  void should_log_nothing_when_info_is_off() throws Exception {
    try (LogCapture logs = LogCapture.of(RequestTimingFilter.class, Level.WARN)) {
      filter.doFilter(request("/api/overview/metrics", null), response, new MockFilterChain());

      assertTrue(logs.messages().isEmpty());
    }
  }
}
