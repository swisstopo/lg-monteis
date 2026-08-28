package ch.swisstopo.monteis.core.infrastructure.jooq;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;

/**
 * Translates a generic {@link PagedRequest} into jOOQ {@link Condition}/{@link SortField}s. Each
 * module supplies its own {@code colId -> jOOQ Field} mapping (jOOQ fields are inherently
 * module-specific), so this is the one place the sort/filter iteration and dispatch logic lives.
 */
public final class PagedRequestJooqTranslator {

  private PagedRequestJooqTranslator() {}

  public record JooqPageCriteria(Condition condition, Collection<SortField<?>> sortFields) {}

  /**
   * @param columnsByColId maps ag-grid {@code colId}s to the jOOQ field they sort/filter on
   * @param defaultSort used when the request has no sort model, to keep offset-based paging
   *     stable across requests
   */
  public static JooqPageCriteria translate(
      PagedRequest request, Map<String, Field<?>> columnsByColId, SortField<?> defaultSort) {
    Condition condition = toCondition(request.filterModel(), columnsByColId);
    Collection<SortField<?>> sortFields = toSortFields(request.sortModel(), columnsByColId);
    if (sortFields.isEmpty() && defaultSort != null) {
      sortFields = List.of(defaultSort);
    }
    return new JooqPageCriteria(condition, sortFields);
  }

  private static Collection<SortField<?>> toSortFields(
      List<SortModelItem> sortModel, Map<String, Field<?>> columnsByColId) {
    List<SortField<?>> sortFields = new ArrayList<>();
    for (SortModelItem item : sortModel) {
      Field<?> field = requireField(item.colId(), columnsByColId);
      sortFields.add(
          switch (item.sort()) {
            case ASC -> field.asc();
            case DESC -> field.desc();
          });
    }
    return sortFields;
  }

  private static Condition toCondition(
      Map<String, FilterModelItem> filterModel, Map<String, Field<?>> columnsByColId) {
    Condition condition = DSL.noCondition();
    for (Map.Entry<String, FilterModelItem> entry : filterModel.entrySet()) {
      Field<?> field = requireField(entry.getKey(), columnsByColId);
      condition = condition.and(toCondition(field, entry.getValue()));
    }
    return condition;
  }

  private static Condition toCondition(Field<?> field, FilterModelItem model) {
    return switch (model) {
      case TextFilterModel text -> textCondition(field, text);
      case NumberFilterModel number -> numberCondition(field, number);
      case DateFilterModel date -> dateCondition(field, date);
    };
  }

  private static Condition textCondition(Field<?> raw, TextFilterModel model) {
    Field<String> field = raw.cast(String.class);
    return switch (model.type()) {
      case AgGridFilter.CONTAINS -> field.containsIgnoreCase(model.filter());
      case AgGridFilter.NOT_CONTAINS -> field.notContainsIgnoreCase(model.filter());
      case AgGridFilter.EQUALS -> field.equalIgnoreCase(model.filter());
      case AgGridFilter.NOT_EQUAL -> field.notEqualIgnoreCase(model.filter());
      case AgGridFilter.STARTS_WITH -> field.startsWithIgnoreCase(model.filter());
      case AgGridFilter.ENDS_WITH -> field.endsWithIgnoreCase(model.filter());
      case AgGridFilter.BLANK -> field.isNull().or(field.eq(""));
      case AgGridFilter.NOT_BLANK -> field.isNotNull().and(field.ne(""));
      case null, default ->
          throw new InvalidPagedRequestException("Unsupported text filter type: " + model.type());
    };
  }

  private static Condition numberCondition(Field<?> raw, NumberFilterModel model) {
    // Cast rather than an unchecked (Field<Double>) cast: the underlying column may be any
    // numeric type (Integer, Double, ...) depending on the module, and .cast() coerces it to
    // Double at the SQL level regardless, instead of failing at bind time on a type mismatch.
    Field<Double> field = raw.cast(Double.class);
    return switch (model.type()) {
      case AgGridFilter.EQUALS -> field.eq(model.filter());
      case AgGridFilter.NOT_EQUAL -> field.ne(model.filter());
      case AgGridFilter.LESS_THAN -> field.lt(model.filter());
      case AgGridFilter.LESS_THAN_OR_EQUAL -> field.le(model.filter());
      case AgGridFilter.GREATER_THAN -> field.gt(model.filter());
      case AgGridFilter.GREATER_THAN_OR_EQUAL -> field.ge(model.filter());
      case AgGridFilter.IN_RANGE -> field.between(model.filter(), model.filterTo());
      case AgGridFilter.BLANK -> field.isNull();
      case AgGridFilter.NOT_BLANK -> field.isNotNull();
      case null, default ->
          throw new InvalidPagedRequestException("Unsupported number filter type: " + model.type());
    };
  }

  private static Condition dateCondition(Field<?> raw, DateFilterModel model) {
    Field<LocalDate> field = raw.cast(LocalDate.class);

    LocalDate fromDate = model.dateFrom() != null ? LocalDate.parse(model.dateFrom()) : null;

    LocalDate toDate = model.dateTo() != null ? LocalDate.parse(model.dateTo()) : null;

    return switch (model.type()) {
      case AgGridFilter.EQUALS -> field.eq(fromDate);
      case AgGridFilter.NOT_EQUAL -> field.ne(fromDate);
      case AgGridFilter.LESS_THAN -> field.lt(fromDate);
      case AgGridFilter.LESS_THAN_OR_EQUAL -> field.le(fromDate);
      case AgGridFilter.GREATER_THAN -> field.gt(fromDate);
      case AgGridFilter.GREATER_THAN_OR_EQUAL -> field.ge(fromDate);
      case AgGridFilter.IN_RANGE -> field.between(fromDate, toDate);
      case AgGridFilter.BLANK -> field.isNull();
      case AgGridFilter.NOT_BLANK -> field.isNotNull();
      case null, default ->
          throw new InvalidPagedRequestException("Unsupported date filter type: " + model.type());
    };
  }

  private static final class AgGridFilter {
    static final String EQUALS = "equals";
    static final String NOT_EQUAL = "notEqual";
    static final String LESS_THAN = "lessThan";
    static final String LESS_THAN_OR_EQUAL = "lessThanOrEqual";
    static final String GREATER_THAN = "greaterThan";
    static final String GREATER_THAN_OR_EQUAL = "greaterThanOrEqual";
    static final String IN_RANGE = "inRange";
    static final String BLANK = "blank";
    static final String NOT_BLANK = "notBlank";
    // text specific
    static final String CONTAINS = "contains";
    static final String NOT_CONTAINS = "notContains";
    static final String STARTS_WITH = "startsWith";
    static final String ENDS_WITH = "endsWith";
  }

  private static Field<?> requireField(String colId, Map<String, Field<?>> columnsByColId) {
    Field<?> field = columnsByColId.get(colId);
    if (field == null) {
      throw new InvalidPagedRequestException("Unknown sortable/filterable column: " + colId);
    }
    return field;
  }
}
