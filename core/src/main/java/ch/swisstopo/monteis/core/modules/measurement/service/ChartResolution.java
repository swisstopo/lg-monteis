package ch.swisstopo.monteis.core.modules.measurement.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Picks the time bucket a chart series is downsampled to, so that a series never carries more than
 * roughly {@link #MAX_POINTS} points regardless of the requested range.
 *
 * <p>The bucket is the smallest step on {@link #BUCKETS} that is at least {@code range /
 * MAX_POINTS}; ranges beyond the ladder fall back to whole days. Ranges up to {@link
 * #RAW_MAX_RANGE} are returned at full resolution.
 */
final class ChartResolution {

  static final int MAX_POINTS = 1500;
  static final Duration RAW_MAX_RANGE = Duration.ofHours(1);

  private static final List<Duration> BUCKETS =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(10),
          Duration.ofMinutes(15),
          Duration.ofMinutes(30),
          Duration.ofHours(1),
          Duration.ofHours(2),
          Duration.ofHours(3),
          Duration.ofHours(6),
          Duration.ofHours(12),
          Duration.ofDays(1));

  private ChartResolution() {}

  /**
   * @return the bucket width, or empty when the range is short enough to be served raw
   * Returns a fallback bucket width if target is bigger than 1d:
   *    target = 10 years
   *    target = 3650d / 1500 = 2,433 Tage
   *    checked: 1d < 2,433d -> no match
   *     ceilDiv(2,433d, 1d) = 3
   *     → Bucket = 3 days → 3650/3 ≈ 1217 points ✓
   */
  static Optional<Duration> bucketFor(OffsetDateTime from, OffsetDateTime to) {
    Duration range = Duration.between(from, to);
    if (range.compareTo(RAW_MAX_RANGE) <= 0) {
      return Optional.empty();
    }
    Duration target = range.dividedBy(MAX_POINTS);
    return Optional.of(
        BUCKETS.stream()
            .filter(bucket -> bucket.compareTo(target) >= 0)
            .findFirst()
            .orElseGet(() -> Duration.ofDays(ceilDiv(target, Duration.ofDays(1)))));
  }

  private static long ceilDiv(Duration dividend, Duration divisor) {
    return Math.ceilDiv(dividend.toNanos(), divisor.toNanos());
  }
}
