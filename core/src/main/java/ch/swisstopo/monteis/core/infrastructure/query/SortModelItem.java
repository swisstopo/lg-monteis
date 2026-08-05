package ch.swisstopo.monteis.core.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors a single entry of ag-grid's {@code sortModel}. {@code ignoreUnknown = true} since
 * ag-grid sends additional fields (e.g. {@code type}) we don't need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SortModelItem(String colId, SortDirection sort) {}
