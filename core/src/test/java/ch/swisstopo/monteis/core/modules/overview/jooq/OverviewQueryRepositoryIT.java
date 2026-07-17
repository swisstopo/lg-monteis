package ch.swisstopo.monteis.core.modules.overview.jooq;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.ITConfiguration.IT;
import ch.swisstopo.monteis.core.ITConfiguration.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IT
class OverviewQueryRepositoryIT {

  @Autowired private OverviewQueryRepository repository;

  @Test
  void should_fetch_recent_metrics_ordered_desc_and_limited_from_seed() {
    // Arrange: We rely entirely on the Flyway/Liquibase seeding script.
    // Run as admin: this test exercises ordering/limiting, not row-level security itself,
    // so it needs full visibility across all seeded readings regardless of experiment linkage.
    int limit = 5;

    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          List<ReadSimpleMetricDto> results = repository.fetchRecentMetrics(limit);

          // Assert: Basic bounds and constraints
          assertNotNull(results, "Results list should not be null");
          assertEquals(limit, results.size(), "Should return exactly the limit size requested");

          // Assert: Verify the integrity of the seeded data
          ReadSimpleMetricDto topRecord = results.getFirst();

          assertAll(
              "Seeded data constraints",
              () -> assertNotNull(topRecord.timestamp(), "Timestamp must exist"),
              () ->
                  assertTrue(
                      Set.of("TEMP-1", "PRESS-1&2", "DISP-2", "FLOW-2", "FLOW-Admin")
                          .contains(topRecord.sensorId()),
                      "Sensor ID must be from the seeded set"),
              () -> assertNotNull(topRecord.rawValue(), "Raw value must be calculated"),
              () -> assertNotNull(topRecord.normValue(), "Norm value must be calculated"),
              () ->
                  assertEquals(
                      (short) 0, topRecord.version(), "Seed script hardcodes version to 0"),
              () -> assertNotNull(topRecord.status(), "Status enum must be mapped"));

          // Assert: Verify the DESCENDING sort order logic of the repository
          for (int i = 0; i < results.size() - 1; i++) {
            OffsetDateTime current = results.get(i).timestamp();
            OffsetDateTime next = results.get(i + 1).timestamp();

            // Because the seed uses a CROSS JOIN, there will be 3 records with the EXACT
            // same timestamp. The 4th record will be exactly 5 minutes older.
            // Therefore, current must be either AFTER or EQUAL TO next.
            boolean isDescSorted = current.isAfter(next) || current.isEqual(next);

            assertTrue(isDescSorted, "Records must be sorted by timestamp descending");
          }
        });
  }
}
