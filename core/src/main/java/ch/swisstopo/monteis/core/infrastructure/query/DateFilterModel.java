package ch.swisstopo.monteis.core.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors ag-grid's {@code agDateColumnFilter} model. {@code type} is ag-grid's operator
 * date, e.g. {@code equals}, {@code lessThan}, {@code inRange}, {@code blank}. {@code
 * ignoreUnknown = true} since the {@code filterType} discriminator (kept in the payload by
 * {@link FilterModelItem}'s {@code EXISTING_PROPERTY} strategy) and other ag-grid fields aren't
 * needed here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DateFilterModel(String type, String dateFrom, String dateTo)
    implements FilterModelItem {}
