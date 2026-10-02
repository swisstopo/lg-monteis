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
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.impl.DSL;

/**
 * The {@code owners} column filters on the experiment_owner join table, which a single column
 * field in {@code COLUMNS_BY_COL_ID} cannot express. It is filter only, the names to sort by live
 * in Keycloak.
 */
final class ExperimentOwnerFilter {

  static final String COL_ID = "owners";

  private ExperimentOwnerFilter() {}

  static PagedRequest withoutOwnerFilter(PagedRequest request) {
    if (!request.filterModel().containsKey(COL_ID)) {
      return request;
    }
    Map<String, FilterModelItem> remaining = new HashMap<>(request.filterModel());
    remaining.remove(COL_ID);
    return new PagedRequest(request.startRow(), request.endRow(), request.sortModel(), remaining);
  }

  /** A null value in the set means "no owner", same convention as the other set filters. */
  static Condition condition(PagedRequest request) {
    FilterModelItem item = request.filterModel().get(COL_ID);
    if (item == null) {
      return DSL.noCondition();
    }
    if (!(item instanceof SetFilterModel(var _, var values))) {
      throw new InvalidPagedRequestException("The owners column only supports a set filter");
    }
    if (values == null || values.isEmpty()) {
      return DSL.falseCondition();
    }

    List<UUID> ownerIds =
        values.stream().filter(Objects::nonNull).map(ExperimentOwnerFilter::toUuid).toList();
    boolean includeWithoutOwner = ownerIds.size() != values.size();

    Condition condition =
        ownerIds.isEmpty()
            ? DSL.falseCondition()
            : DSL.exists(
                DSL.selectOne()
                    .from(EXPERIMENT_OWNER)
                    .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
                    .and(EXPERIMENT_OWNER.USER_ID.in(ownerIds)));
    return includeWithoutOwner
        ? condition.or(
            DSL.notExists(
                DSL.selectOne()
                    .from(EXPERIMENT_OWNER)
                    .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(EXPERIMENTS.ID))))
        : condition;
  }

  private static UUID toUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidPagedRequestException("Owner filter value is not a user id: " + value, e);
    }
  }
}
