package ch.swisstopo.monteis.core.infrastructure.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * Minimal RFC4180 CSV row writer: quotes a field if it contains a comma, quote, or newline,
 * doubling any embedded quotes. Kept dependency-free - the fields written by CSV exports in this
 * codebase are short, mostly-validated scalars, never warranting a full CSV library.
 */
public final class CsvWriter {

  private CsvWriter() {}

  public static void writeRow(Writer writer, List<?> values) throws IOException {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        writer.write(',');
      }
      writer.write(escape(Objects.toString(values.get(i), "")));
    }
    writer.write("\r\n");
  }

  private static String escape(String value) {
    if (value.indexOf(',') < 0
        && value.indexOf('"') < 0
        && value.indexOf('\n') < 0
        && value.indexOf('\r') < 0) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
