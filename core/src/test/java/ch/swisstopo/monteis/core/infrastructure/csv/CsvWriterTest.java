package ch.swisstopo.monteis.core.infrastructure.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvWriterTest {

  @Test
  void should_write_plain_values_semicolon_separated() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("name", "das", "active"));

    // then
    assertEquals("name;das;active\r\n", writer.toString());
  }

  @Test
  void should_write_non_string_values_via_their_string_representation() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of(42, true, 1.5));

    // then
    assertEquals("42;true;1.5\r\n", writer.toString());
  }

  @Test
  void should_quote_a_value_containing_the_separator() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of("Loud; but fine", "a, b"));

    // then
    assertEquals("\"Loud; but fine\";a, b\r\n", writer.toString());
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
    assertEquals("\"line one\nline two\";\"a\rb\"\r\n", writer.toString());
  }

  @Test
  void should_write_null_values_as_empty_fields() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when: Arrays.asList (unlike List.of) permits null elements, as real jOOQ rows do for
    // nullable columns
    CsvWriter.writeRow(writer, Arrays.asList("name", null, "comment"));

    // then
    assertEquals("name;;comment\r\n", writer.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"=HYPERLINK(A1)", "+1", "-1", "@SUM(A1)", "\tcmd"})
  void should_prefix_text_starting_like_a_formula_with_an_apostrophe(String text)
      throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of(text));

    // then
    assertEquals("'" + text + "\r\n", writer.toString());
  }

  @Test
  void should_not_prefix_a_negative_number() throws IOException {
    // given
    StringWriter writer = new StringWriter();

    // when
    CsvWriter.writeRow(writer, List.of(-1.5, -3));

    // then
    assertEquals("-1.5;-3\r\n", writer.toString());
  }
}
