package ch.swisstopo.monteis.core.infrastructure.query;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Mirrors ag-grid's {@code agDateColumnFilter} model. {@code type} is ag-grid's operator
 * date, e.g. {@code equals}, {@code lessThan}, {@code inRange}, {@code blank}. {@code
 * ignoreUnknown = true} since the {@code filterType} discriminator (kept in the payload by
 * {@link FilterModelItem}'s {@code EXISTING_PROPERTY} strategy) and other ag-grid fields aren't
 * needed here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DateFilterModel(String type, String dateFrom, String dateTo)
    implements FilterModelItem {

  public DateFilterModel {
    try {
      if (dateFrom != null) {
        LocalDate.parse(dateFrom);
      }
      if (dateTo != null) {
        LocalDate.parse(dateTo);
      }
    } catch (DateTimeParseException e) {
      throw new InvalidPagedRequestException("Invalid date format provided in DateFilterModel", e);
    }
  }
}
