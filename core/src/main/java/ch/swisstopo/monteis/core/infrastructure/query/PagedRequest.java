package ch.swisstopo.monteis.core.infrastructure.query;

import java.util.List;
import java.util.Map;

/**
 * Generic ag-grid Infinite Row Model request: a page (startRow/endRow), an optional sort model
 * and an optional filter model. Framework/DB-agnostic on purpose so it can sit in a module's
 * *Query interface without leaking jOOQ; translation to jOOQ Condition/SortField happens only in
 * the jOOQ repository layer.
 */
public record PagedRequest(
    int startRow,
    int endRow,
    List<SortModelItem> sortModel,
    Map<String, FilterModelItem> filterModel) {

  public PagedRequest {
    sortModel = sortModel == null ? List.of() : sortModel;
    filterModel = filterModel == null ? Map.of() : filterModel;
  }

  public int limit() {
    return Math.max(0, endRow - startRow);
  }

  public int offset() {
    return startRow;
  }
}
