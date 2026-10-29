package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_ORGANISATION;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.FilterModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SetFilterModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

/**
 * The set filter of the organisations column, on the organisation names like the main experiment
 * column of the sensors. An experiment has several organisations, so it matches when one of them
 * is selected, which the generic set filter on a single column cannot express.
 */
final class ExperimentOrganisationFilter {

  static final String ORGANISATIONS_COLUMN = "organisationIds";

  record Split(PagedRequest requestWithoutOrganisationsColumn, Condition organisationCondition) {}

  private ExperimentOrganisationFilter() {}

  static Split split(PagedRequest request) {
    FilterModelItem organisationsFilter = request.filterModel().get(ORGANISATIONS_COLUMN);
    return new Split(
        withoutOrganisationsColumn(request), organisationCondition(organisationsFilter));
  }

  private static PagedRequest withoutOrganisationsColumn(PagedRequest request) {
    Map<String, FilterModelItem> otherColumns = new HashMap<>(request.filterModel());
    otherColumns.remove(ORGANISATIONS_COLUMN);
    return new PagedRequest(
        request.startRow(), request.endRow(), request.sortModel(), otherColumns);
  }

  private static Condition organisationCondition(FilterModelItem organisationsFilter) {
    if (organisationsFilter == null) {
      return DSL.noCondition();
    }
    Set<String> selected = selectedValuesOf(organisationsFilter);
    Condition hasSelectedOrganisation = hasOrganisationAmong(selectedNames(selected));
    if (isNoOrganisationSelected(selected)) {
      return hasSelectedOrganisation.or(hasNoOrganisation());
    }
    return hasSelectedOrganisation;
  }

  private static Set<String> selectedValuesOf(FilterModelItem organisationsFilter) {
    if (organisationsFilter instanceof SetFilterModel(_, var values)) {
      return values == null ? Set.of() : values;
    }
    throw new InvalidPagedRequestException("The organisations column only supports a set filter");
  }

  private static List<String> selectedNames(Set<String> selected) {
    return selected.stream().filter(Objects::nonNull).toList();
  }

  private static boolean isNoOrganisationSelected(Set<String> selected) {
    return selected.stream().anyMatch(Objects::isNull);
  }

  private static Condition hasOrganisationAmong(List<String> names) {
    if (names.isEmpty()) {
      return DSL.falseCondition();
    }
    return DSL.exists(
        organisationRowsOfTheExperiment()
            .andExists(
                DSL.selectOne()
                    .from(ORGANISATIONS)
                    .where(ORGANISATIONS.ID.eq(EXPERIMENT_ORGANISATION.ORGANISATION_ID))
                    .and(ORGANISATIONS.NAME.in(names))));
  }

  private static Condition hasNoOrganisation() {
    return DSL.notExists(organisationRowsOfTheExperiment());
  }

  private static SelectConditionStep<Record1<Integer>> organisationRowsOfTheExperiment() {
    return DSL.selectOne()
        .from(EXPERIMENT_ORGANISATION)
        .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.eq(EXPERIMENTS.ID));
  }
}
