package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_ORGANISATION;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_OWNER;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.FilterModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SetFilterModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;

/**
 * The set filter of a column backed by a link table of the experiments, on the linked ids. An
 * experiment has several linked rows, so it matches when one of them is selected, which the
 * generic set filter on a single column cannot express. The null value selects the experiments
 * without any linked row.
 *
 * @param column the colId of the grid column
 * @param experimentId the link table's reference to the experiment
 * @param linkedId the link table's reference to the linked row, the values of the filter
 */
record LinkTableSetFilter(
    String column, TableField<?, UUID> experimentId, TableField<?, UUID> linkedId) {

  static final LinkTableSetFilter OWNERS =
      new LinkTableSetFilter("owners", EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID);

  static final LinkTableSetFilter ORGANISATIONS =
      new LinkTableSetFilter(
          "organisationIds",
          EXPERIMENT_ORGANISATION.EXPERIMENT_ID,
          EXPERIMENT_ORGANISATION.ORGANISATION_ID);

  record Split(PagedRequest requestWithoutColumn, Condition condition) {}

  Split split(PagedRequest request) {
    FilterModelItem filter = request.filterModel().get(column);
    return new Split(withoutColumn(request), condition(filter));
  }

  private PagedRequest withoutColumn(PagedRequest request) {
    Map<String, FilterModelItem> otherColumns = new HashMap<>(request.filterModel());
    otherColumns.remove(column);
    return new PagedRequest(
        request.startRow(), request.endRow(), request.sortModel(), otherColumns);
  }

  private Condition condition(FilterModelItem filter) {
    if (filter == null) {
      return DSL.noCondition();
    }
    Set<String> selected = selectedValuesOf(filter);
    Condition hasSelectedRow = hasLinkedRowAmong(selectedIds(selected));
    // not contains(null): an immutable Set.of throws on it
    if (selected.stream().anyMatch(Objects::isNull)) {
      return hasSelectedRow.or(hasNoLinkedRow());
    }
    return hasSelectedRow;
  }

  private Set<String> selectedValuesOf(FilterModelItem filter) {
    if (filter instanceof SetFilterModel(_, var values)) {
      return values == null ? Set.of() : values;
    }
    throw new InvalidPagedRequestException("The " + column + " column only supports a set filter");
  }

  private List<UUID> selectedIds(Set<String> selected) {
    return selected.stream().filter(Objects::nonNull).map(this::toId).toList();
  }

  private Condition hasLinkedRowAmong(List<UUID> ids) {
    if (ids.isEmpty()) {
      return DSL.falseCondition();
    }
    return DSL.exists(linkedRowsOfTheExperiment().and(linkedId.in(ids)));
  }

  private Condition hasNoLinkedRow() {
    return DSL.notExists(linkedRowsOfTheExperiment());
  }

  private SelectConditionStep<Record1<Integer>> linkedRowsOfTheExperiment() {
    return DSL.selectOne().from(experimentId.getTable()).where(experimentId.eq(EXPERIMENTS.ID));
  }

  private UUID toId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidPagedRequestException(
          "The " + column + " filter value is not an id: " + value, e);
    }
  }
}
