package ch.swisstopo.monteis.core.infrastructure.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

  @Test
  void should_write_plain_values_comma_separated() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("name", "das", "active"));

    // then
    assertEquals("name,das,active\r\n", writer.toString());
  }

  @Test
  void should_write_non_string_values_via_their_string_representation() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of(42, true, 1.5));

    // then
    assertEquals("42,true,1.5\r\n", writer.toString());
  }

  @Test
  void should_quote_a_value_containing_a_comma() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("Loud, but fine"));

    // then
    assertEquals("\"Loud, but fine\"\r\n", writer.toString());
  }

  @Test
  void should_quote_and_double_up_embedded_quotes() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("She said \"hi\""));

    // then
    assertEquals("\"She said \"\"hi\"\"\"\r\n", writer.toString());
  }

  @Test
  void should_quote_a_value_containing_a_newline_or_carriage_return() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("line one\nline two", "a\rb"));

    // then
    assertEquals("\"line one\nline two\",\"a\rb\"\r\n", writer.toString());
  }

  @Test
  void should_write_null_values_as_empty_fields() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when: Arrays.asList (unlike List.of) permits null elements, as real jOOQ rows do for
    // nullable columns
    CsvWriter.writeRow(writer, Arrays.asList("name", null, "comment"));

    // then
    assertEquals("name,,comment\r\n", writer.toString());
  }
}
