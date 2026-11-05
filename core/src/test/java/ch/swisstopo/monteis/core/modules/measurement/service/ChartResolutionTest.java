package ch.swisstopo.monteis.core.modules.measurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChartResolutionTest {

  private static final OffsetDateTime FROM = OffsetDateTime.parse("2024-01-01T00:00:00Z");

  @Test
  void should_serve_raw_when_range_is_empty() {
    assertEquals(Optional.empty(), ChartResolution.bucketFor(FROM, FROM));
  }

  @Test
  void should_serve_raw_when_range_is_exactly_the_raw_limit() {
    OffsetDateTime to = FROM.plus(ChartResolution.RAW_MAX_RANGE);

    assertEquals(Optional.empty(), ChartResolution.bucketFor(FROM, to));
  }

  @Test
  void should_bucket_by_one_minute_just_above_the_raw_limit() {
    OffsetDateTime to = FROM.plus(ChartResolution.RAW_MAX_RANGE).plusNanos(1_000);

    assertEquals(Optional.of(Duration.ofMinutes(1)), ChartResolution.bucketFor(FROM, to));
  }

  @ParameterizedTest
  @CsvSource({
    "PT24H, PT1M",
    "P7D, PT10M",
    "P31D, PT30M",
    "P365D, PT6H",
    "P366D, PT6H",
    "P1500D, P1D",
    "P3000D, P2D"
  })
  void should_pick_smallest_bucket_that_keeps_points_within_limit(
      String range, String expectedBucket) {
    OffsetDateTime to = FROM.plus(Duration.parse(range));

    assertEquals(Optional.of(Duration.parse(expectedBucket)), ChartResolution.bucketFor(FROM, to));
  }

  @ParameterizedTest
  @CsvSource({"PT1H1M", "PT25H", "PT25H1M", "P7D", "P31D", "P366D", "P3000D"})
  void should_never_exceed_max_points(String range) {
    Duration duration = Duration.parse(range);

    Duration bucket = ChartResolution.bucketFor(FROM, FROM.plus(duration)).orElseThrow();

    // +1 for a partially filled bucket at each edge of an unaligned range
    long maxBuckets = Math.ceilDiv(duration.toNanos(), bucket.toNanos()) + 1;
    assertTrue(
        maxBuckets <= ChartResolution.MAX_POINTS + 1,
        range + " with bucket " + bucket + " yields up to " + maxBuckets + " points");
  }
}
