package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class ExperimentOwnerFilterTest {

  private static final String ALICE = UUID.randomUUID().toString();
  private static final FilterModelItem NAME_FILTER = new TextFilterModel("contains", "Alpha", null);

  @Test
  void should_take_the_owners_column_out_of_the_request() {
    PagedRequest request = request(Map.of("name", NAME_FILTER, "owners", owners(ALICE)));

    PagedRequest withoutOwners = ExperimentOwnerFilter.split(request).requestWithoutOwnersColumn();

    assertEquals(Map.of("name", NAME_FILTER), withoutOwners.filterModel());
  }

  @Test
  void should_not_filter_without_an_owners_filter() {
    assertEquals(DSL.noCondition(), ownerCondition(request(Map.of("name", NAME_FILTER))));
  }

  @Test
  void should_match_nothing_when_nothing_is_selected() {
    assertEquals(DSL.falseCondition(), ownerCondition(request(Map.of("owners", owners()))));
  }

  @Test
  void should_match_experiments_with_one_of_the_selected_owners() {
    String sql = sql(ownerCondition(request(Map.of("owners", owners(ALICE)))));

    assertTrue(sql.startsWith("exists"), sql);
    assertTrue(sql.contains(ALICE), sql);
    assertFalse(sql.contains("not exists"), sql);
  }

  @Test
  void should_match_experiments_without_owner_for_the_no_owner_entry() {
    String sql = sql(ownerCondition(request(Map.of("owners", owners((String) null)))));

    assertTrue(sql.contains("not exists"), sql);
    assertFalse(sql.contains(" in ("), sql);
  }

  @Test
  void should_match_either_for_an_owner_and_the_no_owner_entry() {
    String sql = sql(ownerCondition(request(Map.of("owners", owners(ALICE, null)))));

    assertTrue(sql.contains("exists") && sql.contains(" or not exists"), sql);
  }

  @Test
  void should_reject_anything_but_a_set_filter() {
    PagedRequest request = request(Map.of("owners", NAME_FILTER));

    assertThrows(InvalidPagedRequestException.class, () -> ExperimentOwnerFilter.split(request));
  }

  @Test
  void should_reject_a_value_that_is_not_a_user_id() {
    PagedRequest request = request(Map.of("owners", owners("alice")));

    assertThrows(InvalidPagedRequestException.class, () -> ExperimentOwnerFilter.split(request));
  }

  private static Condition ownerCondition(PagedRequest request) {
    return ExperimentOwnerFilter.split(request).ownerCondition();
  }

  private static SetFilterModel owners(String... values) {
    return new SetFilterModel("set", new HashSet<>(Arrays.asList(values)));
  }

  private static PagedRequest request(Map<String, FilterModelItem> filterModel) {
    return new PagedRequest(0, 10, List.of(), filterModel);
  }

  private static String sql(Condition condition) {
    return DSL.using(SQLDialect.POSTGRES).renderInlined(condition);
  }
}
