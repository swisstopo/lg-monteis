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
      case "contains" -> field.containsIgnoreCase(model.filter());
      case "notContains" -> field.notContainsIgnoreCase(model.filter());
      case "equals" -> field.equalIgnoreCase(model.filter());
      case "notEqual" -> field.notEqualIgnoreCase(model.filter());
      case "startsWith" -> field.startsWithIgnoreCase(model.filter());
      case "endsWith" -> field.endsWithIgnoreCase(model.filter());
      case "blank" -> field.isNull().or(field.eq(""));
      case "notBlank" -> field.isNotNull().and(field.ne(""));
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
      case "equals" -> field.eq(model.filter());
      case "notEqual" -> field.ne(model.filter());
      case "lessThan" -> field.lt(model.filter());
      case "lessThanOrEqual" -> field.le(model.filter());
      case "greaterThan" -> field.gt(model.filter());
      case "greaterThanOrEqual" -> field.ge(model.filter());
      case "inRange" -> field.between(model.filter(), model.filterTo());
      case "blank" -> field.isNull();
      case "notBlank" -> field.isNotNull();
      case null, default ->
          throw new InvalidPagedRequestException("Unsupported number filter type: " + model.type());
    };
  }

  private static Condition dateCondition(Field<?> raw, DateFilterModel model) {
    Field<LocalDate> field = raw.cast(LocalDate.class);

    LocalDate fromDate = model.dateFrom() != null ? LocalDate.parse(model.dateFrom()) : null;

    LocalDate toDate = model.dateTo() != null ? LocalDate.parse(model.dateTo()) : null;

    return switch (model.type()) {
      case "equals" -> field.eq(fromDate);
      case "notEqual" -> field.ne(fromDate);
      case "lessThan" -> field.lt(fromDate);
      case "lessThanOrEqual" -> field.le(fromDate);
      case "greaterThan" -> field.gt(fromDate);
      case "greaterThanOrEqual" -> field.ge(fromDate);
      case "inRange" -> field.between(fromDate, toDate);
      case "blank" -> field.isNull();
      case "notBlank" -> field.isNotNull();
      case null, default ->
          throw new InvalidPagedRequestException("Unsupported date filter type: " + model.type());
    };
  }

  private static Field<?> requireField(String colId, Map<String, Field<?>> columnsByColId) {
    Field<?> field = columnsByColId.get(colId);
    if (field == null) {
      throw new InvalidPagedRequestException("Unknown sortable/filterable column: " + colId);
    }
    return field;
  }
}
