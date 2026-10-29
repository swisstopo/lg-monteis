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
import org.jooq.Condition;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class ExperimentOrganisationFilterTest {

  private static final FilterModelItem NAME_FILTER = new TextFilterModel("contains", "Alpha", null);

  @Test
  void should_take_the_organisations_column_out_of_the_request() {
    PagedRequest request =
        request(Map.of("name", NAME_FILTER, "organisationIds", organisations("swisstopo")));

    PagedRequest withoutOrganisations =
        ExperimentOrganisationFilter.split(request).requestWithoutOrganisationsColumn();

    assertEquals(Map.of("name", NAME_FILTER), withoutOrganisations.filterModel());
  }

  @Test
  void should_not_filter_without_an_organisations_filter() {
    assertEquals(DSL.noCondition(), organisationCondition(request(Map.of("name", NAME_FILTER))));
  }

  @Test
  void should_match_nothing_when_nothing_is_selected() {
    assertEquals(
        DSL.falseCondition(),
        organisationCondition(request(Map.of("organisationIds", organisations()))));
  }

  @Test
  void should_match_experiments_with_one_of_the_selected_organisations_by_name() {
    String sql =
        sql(organisationCondition(request(Map.of("organisationIds", organisations("swisstopo")))));

    assertTrue(sql.startsWith("exists"), sql);
    assertTrue(sql.contains("'swisstopo'"), sql);
    assertFalse(sql.contains("not exists"), sql);
  }

  @Test
  void should_match_experiments_without_organisation_for_the_no_organisation_entry() {
    String sql =
        sql(
            organisationCondition(
                request(Map.of("organisationIds", organisations("swisstopo", null)))));

    assertTrue(sql.contains(" or not exists"), sql);
  }

  @Test
  void should_reject_anything_but_a_set_filter() {
    PagedRequest request = request(Map.of("organisationIds", NAME_FILTER));

    assertThrows(
        InvalidPagedRequestException.class, () -> ExperimentOrganisationFilter.split(request));
  }

  private static Condition organisationCondition(PagedRequest request) {
    return ExperimentOrganisationFilter.split(request).organisationCondition();
  }

  private static SetFilterModel organisations(String... values) {
    return new SetFilterModel("set", new HashSet<>(Arrays.asList(values)));
  }

  private static PagedRequest request(Map<String, FilterModelItem> filterModel) {
    return new PagedRequest(0, 10, List.of(), filterModel);
  }

  private static String sql(Condition condition) {
    return DSL.using(SQLDialect.POSTGRES).renderInlined(condition);
  }
}
