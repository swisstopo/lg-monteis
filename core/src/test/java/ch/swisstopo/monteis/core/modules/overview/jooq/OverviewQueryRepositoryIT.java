package ch.swisstopo.monteis.core.modules.overview.jooq;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.query.FilterModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.NumberFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
                      Set.of(
                              "SOL_EXPERTS__TEMP-1__temperature",
                              "SOL_EXPERTS__PRESS-1&2__pressure",
                              "SOL_EXPERTS__DISP-2__displacement",
                              "SOL_EXPERTS__FLOW-2__flow",
                              "SOL_EXPERTS__FLOW-Admin__flow")
                          .contains(topRecord.dasKey()),
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

  @Test
  void should_page_metrics_without_overlap_between_consecutive_pages() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          PagedResult<ReadSimpleMetricDto> firstPage =
              repository.findPagedMetrics(new PagedRequest(0, 5, List.of(), Map.of()));
          PagedResult<ReadSimpleMetricDto> secondPage =
              repository.findPagedMetrics(new PagedRequest(5, 10, List.of(), Map.of()));

          assertEquals(5, firstPage.rows().size(), "Should return exactly the requested page size");
          assertTrue(firstPage.totalCount() >= 10, "Seed must hold at least two pages of readings");
          assertEquals(
              firstPage.totalCount(),
              secondPage.totalCount(),
              "Total count must not depend on the page requested");

          // The seed ties timestamps across sensors (CROSS JOIN), so this is the case the
          // primary-key tie-break in findPagedMetrics exists for.
          Set<String> firstPageKeys = rowKeys(firstPage);
          assertTrue(
              rowKeys(secondPage).stream().noneMatch(firstPageKeys::contains),
              "Consecutive pages must not repeat rows");
        });
  }

  @Test
  void should_apply_the_requested_sort_model() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          PagedResult<ReadSimpleMetricDto> page =
              repository.findPagedMetrics(
                  new PagedRequest(
                      0, 10, List.of(new SortModelItem("timestamp", SortDirection.ASC)), Map.of()));

          for (int i = 0; i < page.rows().size() - 1; i++) {
            OffsetDateTime current = page.rows().get(i).timestamp();
            OffsetDateTime next = page.rows().get(i + 1).timestamp();

            assertTrue(
                current.isBefore(next) || current.isEqual(next),
                "Records must be sorted by timestamp ascending");
          }
        });
  }

  private static Set<String> rowKeys(PagedResult<ReadSimpleMetricDto> page) {
    return page.rows().stream()
        .map(row -> row.timestamp() + "|" + row.dasKey())
        .collect(Collectors.toSet());
  }

  @Test
  void should_apply_the_conditions_of_every_filtered_column_together() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          Map<String, FilterModelItem> filters = new LinkedHashMap<>();
          filters.put("dasKey", new TextFilterModel("contains", "TEMP", null));
          // Seeded raw values follow 50 + 30*sin(...), so "> 70" always matches some readings of
          // every sensor and never all of them.
          filters.put("rawValue", new NumberFilterModel("greaterThan", 70.0, null));

          PagedResult<ReadSimpleMetricDto> bothColumns =
              repository.findPagedMetrics(new PagedRequest(0, 20, List.of(), filters));
          PagedResult<ReadSimpleMetricDto> dasKeyOnly =
              repository.findPagedMetrics(
                  new PagedRequest(
                      0,
                      20,
                      List.of(),
                      Map.of("dasKey", new TextFilterModel("contains", "TEMP", null))));

          assertFalse(bothColumns.rows().isEmpty(), "Seed must hold high temperature readings");
          assertTrue(
              bothColumns.rows().stream()
                  .allMatch(row -> row.dasKey().contains("TEMP") && row.rawValue() > 70),
              "Every row must satisfy both column conditions");
          assertTrue(
              bothColumns.totalCount() < dasKeyOnly.totalCount(),
              "Adding a second column's condition must narrow the result");
        });
  }
}
