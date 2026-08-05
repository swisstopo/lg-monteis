package ch.swisstopo.monteis.core.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors ag-grid's {@code agTextColumnFilter} model. {@code type} is ag-grid's operator string,
 * e.g. {@code contains}, {@code equals}, {@code startsWith}, {@code blank}. {@code ignoreUnknown
 * = true} since the {@code filterType} discriminator (kept in the payload by {@link
 * FilterModelItem}'s {@code EXISTING_PROPERTY} strategy) and other ag-grid fields aren't needed
 * here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextFilterModel(String type, String filter, String filterTo)
    implements FilterModelItem {}
