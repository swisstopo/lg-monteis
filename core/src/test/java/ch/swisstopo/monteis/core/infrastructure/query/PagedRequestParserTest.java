package ch.swisstopo.monteis.core.infrastructure.query;

import static org.junit.jupiter.api.Assertions.*;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/**
 * Exercises the actual JSON parsing path against realistic ag-grid wire payloads (not just
 * hand-built Java records) - ag-grid includes extra fields (e.g. sortModel's {@code type},
 * filterModel's {@code filterType} discriminator) that {@link SortModelItem}/{@link
 * TextFilterModel}/{@link NumberFilterModel} must tolerate rather than reject.
 */
class PagedRequestParserTest {
  private final PagedRequestParser parser = new PagedRequestParser();

  @Test
  void should_parse_sort_model_with_unknown_fields() {
    // given: a real ag-grid sortModel entry, e.g. from ag-grid-community 36's IGetRowsParams
    RawPagedRequest raw =
        new RawPagedRequest(
            0, 20, "[{\"colId\":\"name\",\"sort\":\"asc\",\"type\":\"text\"}]", null);

    // when
    PagedRequest parsed = parser.parse(raw);

    // then
    assertEquals(1, parsed.sortModel().size());
    assertEquals("name", parsed.sortModel().getFirst().colId());
    assertEquals(SortDirection.ASC, parsed.sortModel().getFirst().sort());
  }

  @Test
  void should_parse_text_filter_model_with_discriminator_field() {
    // given: filterType is kept in the payload by EXISTING_PROPERTY and must be tolerated
    RawPagedRequest raw =
        new RawPagedRequest(
            0,
            20,
            null,
            "{\"code\":{\"filterType\":\"text\",\"type\":\"contains\",\"filter\":\"abc\"}}");

    // when
    PagedRequest parsed = parser.parse(raw);

    // then
    FilterModelItem model = parsed.filterModel().get("code");
    assertInstanceOf(TextFilterModel.class, model);
    assertEquals("contains", ((TextFilterModel) model).type());
    assertEquals("abc", ((TextFilterModel) model).filter());
  }

  @Test
  void should_parse_number_filter_model_with_discriminator_field() {
    RawPagedRequest raw =
        new RawPagedRequest(
            0,
            20,
            null,
            "{\"coordinates.x\":{\"filterType\":\"number\",\"type\":\"inRange\",\"filter\":1.0,\"filterTo\":5.0}}");

    // when
    PagedRequest parsed = parser.parse(raw);

    // then
    FilterModelItem model = parsed.filterModel().get("coordinates.x");
    assertInstanceOf(NumberFilterModel.class, model);
    assertEquals("inRange", ((NumberFilterModel) model).type());
    assertEquals(1.0, ((NumberFilterModel) model).filter());
    assertEquals(5.0, ((NumberFilterModel) model).filterTo());
  }

  @Test
  void should_default_to_empty_when_no_sort_or_filter_given() {
    RawPagedRequest raw = new RawPagedRequest(0, 20, null, null);

    PagedRequest parsed = parser.parse(raw);

    assertTrue(parsed.sortModel().isEmpty());
    assertTrue(parsed.filterModel().isEmpty());
  }

  @Test
  void should_default_to_empty_when_sort_or_filter_is_blank() {
    // given: whitespace-only strings, distinct from null - isBlank() must catch these too
    RawPagedRequest raw = new RawPagedRequest(0, 20, "   ", " ");

    PagedRequest parsed = parser.parse(raw);

    assertTrue(parsed.sortModel().isEmpty());
    assertTrue(parsed.filterModel().isEmpty());
  }

  @Test
  void should_wrap_malformed_sort_model_json_syntax() {
    // given: syntactically invalid JSON (unterminated array)
    RawPagedRequest raw = new RawPagedRequest(0, 20, "[{", null);

    // when / then
    InvalidPagedRequestException ex =
        assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
    assertInstanceOf(JsonProcessingException.class, ex.getCause());
  }

  @Test
  void should_wrap_malformed_filter_model_json_syntax() {
    RawPagedRequest raw = new RawPagedRequest(0, 20, null, "{not json");

    InvalidPagedRequestException ex =
        assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
    assertInstanceOf(JsonProcessingException.class, ex.getCause());
  }

  @Test
  void should_wrap_sort_model_with_wrong_json_shape() {
    // given: valid JSON, but an object where an array is expected
    RawPagedRequest raw = new RawPagedRequest(0, 20, "{}", null);

    InvalidPagedRequestException ex =
        assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
    assertInstanceOf(JsonProcessingException.class, ex.getCause());
  }

  @Test
  void should_wrap_filter_model_with_unknown_discriminator() {
    // given: filterType doesn't match any registered FilterModelItem subtype
    RawPagedRequest raw =
        new RawPagedRequest(
            0, 20, null, "{\"code\":{\"filterType\":\"boolean\",\"type\":\"equals\"}}");

    InvalidPagedRequestException ex =
        assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
    assertInstanceOf(JsonProcessingException.class, ex.getCause());
  }

  @Test
  void should_wrap_filter_model_with_missing_discriminator() {
    // given: no filterType at all, so Jackson can't resolve which FilterModelItem subtype to use
    RawPagedRequest raw =
        new RawPagedRequest(0, 20, null, "{\"code\":{\"type\":\"equals\",\"filter\":\"abc\"}}");

    InvalidPagedRequestException ex =
        assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
    assertInstanceOf(JsonProcessingException.class, ex.getCause());
  }

  @Test
  void should_reject_start_row_greater_than_end_row() {
    RawPagedRequest raw = new RawPagedRequest(50, 10, null, null);

    assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
  }

  @Test
  void should_reject_page_size_exceeding_maximum() {
    RawPagedRequest raw = new RawPagedRequest(0, 501, null, null);

    assertThrows(InvalidPagedRequestException.class, () -> parser.parse(raw));
  }

  @Test
  void should_allow_page_size_exactly_at_maximum() {
    RawPagedRequest raw = new RawPagedRequest(0, 500, null, null);

    assertDoesNotThrow(() -> parser.parse(raw));
  }
}
