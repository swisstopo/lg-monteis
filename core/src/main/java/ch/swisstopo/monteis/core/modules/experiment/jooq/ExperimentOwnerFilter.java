package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
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
import org.jooq.impl.DSL;

/**
 * The {@code owners} column filters on the experiment_owner join table, which a single column
 * field in {@code COLUMNS_BY_COL_ID} cannot express. It is filter only, the names to sort by live
 * in Keycloak.
 */
final class ExperimentOwnerFilter {

  static final String OWNERS_COLUMN = "owners";

  /**
   * @param requestWithoutOwnersColumn for the generic translator, which does not know the column
   * @param ownerCondition to add to the query the translator's criteria go into
   */
  record Split(PagedRequest requestWithoutOwnersColumn, Condition ownerCondition) {}

  private ExperimentOwnerFilter() {}

  static Split split(PagedRequest request) {
    FilterModelItem ownersFilter = request.filterModel().get(OWNERS_COLUMN);
    return new Split(withoutOwnersColumn(request), ownerCondition(ownersFilter));
  }

  private static PagedRequest withoutOwnersColumn(PagedRequest request) {
    Map<String, FilterModelItem> otherColumns = new HashMap<>(request.filterModel());
    otherColumns.remove(OWNERS_COLUMN);
    return new PagedRequest(
        request.startRow(), request.endRow(), request.sortModel(), otherColumns);
  }

  /** A null value in the set is "(no owner)", same convention as the other set filters. */
  private static Condition ownerCondition(FilterModelItem ownersFilter) {
    if (ownersFilter == null) {
      return DSL.noCondition();
    }
    Set<String> selected = selectedValuesOf(ownersFilter);
    Condition hasSelectedOwner = hasOwnerAmong(selectedOwnerIds(selected));
    if (isNoOwnerSelected(selected)) {
      return hasSelectedOwner.or(hasNoOwner());
    }
    return hasSelectedOwner;
  }

  private static Set<String> selectedValuesOf(FilterModelItem ownersFilter) {
    if (ownersFilter instanceof SetFilterModel(var _, var values)) {
      return values == null ? Set.of() : values;
    }
    throw new InvalidPagedRequestException("The owners column only supports a set filter");
  }

  private static List<UUID> selectedOwnerIds(Set<String> selected) {
    return selected.stream().filter(Objects::nonNull).map(ExperimentOwnerFilter::toUserId).toList();
  }

  private static boolean isNoOwnerSelected(Set<String> selected) {
    return selected.stream().anyMatch(Objects::isNull);
  }

  private static Condition hasOwnerAmong(List<UUID> userIds) {
    if (userIds.isEmpty()) {
      return DSL.falseCondition();
    }
    return DSL.exists(ownerRowsOfTheExperiment().and(EXPERIMENT_OWNER.USER_ID.in(userIds)));
  }

  private static Condition hasNoOwner() {
    return DSL.notExists(ownerRowsOfTheExperiment());
  }

  private static SelectConditionStep<Record1<Integer>> ownerRowsOfTheExperiment() {
    return DSL.selectOne()
        .from(EXPERIMENT_OWNER)
        .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(EXPERIMENTS.ID));
  }

  private static UUID toUserId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidPagedRequestException("Owner filter value is not a user id: " + value, e);
    }
  }
}
