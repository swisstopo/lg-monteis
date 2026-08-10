package ch.swisstopo.monteis.core.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Mirrors ag-grid's sort direction, which arrives lower-case (e.g. {@code "asc"}). */
public enum SortDirection {
  ASC,
  DESC;

  @JsonCreator
  public static SortDirection fromJson(String value) {
    return SortDirection.valueOf(value.toUpperCase());
  }
}
