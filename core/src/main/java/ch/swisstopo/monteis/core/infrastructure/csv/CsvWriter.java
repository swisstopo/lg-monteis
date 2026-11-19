package ch.swisstopo.monteis.core.infrastructure.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * Minimal RFC4180 CSV row writer: quotes a field if it contains the separator, a quote, or a
 * newline, doubling any embedded quotes. Kept dependency-free - the fields written by CSV exports
 * in this codebase are short, mostly-validated scalars, never warranting a full CSV library.
 *
 * <p>Fields are separated by {@value #SEPARATOR}, the list separator of a Swiss Excel, which
 * otherwise opens the whole row in one column.
 *
 * <p>A text cell starting with one of {@value #FORMULA_TRIGGERS} is prefixed with {@code '}, so
 * spreadsheet programs show user input like {@code =HYPERLINK(...)} as text instead of running it
 * as a formula (CSV injection). Only text is guarded, a negative number stays a number.
 */
public final class CsvWriter {

  static final char SEPARATOR = ';';

  private static final String FORMULA_TRIGGERS = "=+-@\t\r";

  private CsvWriter() {}

  public static void writeRow(Writer writer, List<?> values) throws IOException {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        writer.write(SEPARATOR);
      }
      writer.write(escape(cell(values.get(i))));
    }
    writer.write("\r\n");
  }

  private static String cell(Object value) {
    String text = Objects.toString(value, "");
    if (value instanceof CharSequence
        && !text.isEmpty()
        && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0) {
      return "'" + text;
    }
    return text;
  }

  private static String escape(String value) {
    if (value.indexOf(SEPARATOR) < 0
        && value.indexOf('"') < 0
        && value.indexOf('\n') < 0
        && value.indexOf('\r') < 0) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
