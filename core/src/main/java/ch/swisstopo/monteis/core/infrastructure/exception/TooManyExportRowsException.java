package ch.swisstopo.monteis.core.infrastructure.exception;

/** A CSV export matched more rows than allowed, the user has to narrow it down with filters. */
public class TooManyExportRowsException extends RuntimeException {
  public static final String MESSAGE_KEY = "export.tooManyRows";

  private final int maxRows;

  public TooManyExportRowsException(int maxRows) {
    super("Export matches more than %d rows".formatted(maxRows));
    this.maxRows = maxRows;
  }

  public int getMaxRows() {
    return maxRows;
  }
}
