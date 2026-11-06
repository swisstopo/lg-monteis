package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.FilterModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SetFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * Which experiments the conditions match is tested against the database in {@link
 * JooqExperimentRepositoryIT} and {@link JooqExperimentCsvExportQueryRepositoryIT}.
 */
class LinkTableSetFilterTest {

  private static final LinkTableSetFilter FILTER = LinkTableSetFilter.OWNERS;
  private static final String ALICE = UUID.randomUUID().toString();
  private static final FilterModelItem NAME_FILTER = new TextFilterModel("contains", "Alpha", null);

  @Test
  void should_take_its_column_out_of_the_request() {
    PagedRequest request = request(Map.of("name", NAME_FILTER, "owners", values(ALICE)));

    PagedRequest withoutColumn = FILTER.split(request).requestWithoutColumn();

    assertEquals(Map.of("name", NAME_FILTER), withoutColumn.filterModel());
  }

  @Test
  void should_not_filter_without_a_filter_on_its_column() {
    assertEquals(DSL.noCondition(), condition(request(Map.of("name", NAME_FILTER))));
  }

  @Test
  void should_match_nothing_when_nothing_is_selected() {
    assertEquals(DSL.falseCondition(), condition(request(Map.of("owners", values()))));
  }

  @Test
  void should_reject_anything_but_a_set_filter() {
    PagedRequest request = request(Map.of("owners", NAME_FILTER));

    assertThrows(InvalidPagedRequestException.class, () -> FILTER.split(request));
  }

  @Test
  void should_reject_a_value_that_is_not_an_id() {
    PagedRequest request = request(Map.of("owners", values("alice")));

    assertThrows(InvalidPagedRequestException.class, () -> FILTER.split(request));
  }

  private static Condition condition(PagedRequest request) {
    return FILTER.split(request).condition();
  }

  private static SetFilterModel values(String... values) {
    return new SetFilterModel("set", new HashSet<>(Arrays.asList(values)));
  }

  private static PagedRequest request(Map<String, FilterModelItem> filterModel) {
    return new PagedRequest(0, 10, List.of(), filterModel);
  }
}
