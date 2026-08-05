package ch.swisstopo.monteis.core.infrastructure.jooq;

import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.NumberFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class PagedRequestJooqTranslatorTest {
  private final Map<String, Field<?>> columnsByColId =
      Map.of("name", DSL.field("name", String.class));

  @Test
  void should_reject_unsupported_text_filter_type() {
    // given: a text filter operator PagedRequestJooqTranslator.textCondition doesn't model
    PagedRequest request =
        new PagedRequest(
            0, 20, List.of(), Map.of("name", new TextFilterModel("regex", "abc", null)));

    // when / then
    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }

  @Test
  void should_reject_unsupported_number_filter_type() {
    PagedRequest request =
        new PagedRequest(
            0, 20, List.of(), Map.of("name", new NumberFilterModel("modulo", 1.0, null)));

    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }

  @Test
  void should_reject_unknown_sort_column() {
    PagedRequest request =
        new PagedRequest(
            0, 20, List.of(new SortModelItem("unknownColumn", SortDirection.ASC)), Map.of());

    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }

  @Test
  void should_reject_unknown_filter_column() {
    PagedRequest request =
        new PagedRequest(
            0, 20, List.of(), Map.of("unknownColumn", new TextFilterModel("equals", "abc", null)));

    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }

  @Test
  void should_reject_null_text_filter_type() {
    // given: a filter entry missing/null "type" (e.g. omitted in the request JSON)
    PagedRequest request =
        new PagedRequest(0, 20, List.of(), Map.of("name", new TextFilterModel(null, "abc", null)));

    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }

  @Test
  void should_reject_null_number_filter_type() {
    PagedRequest request =
        new PagedRequest(0, 20, List.of(), Map.of("name", new NumberFilterModel(null, 1.0, null)));

    assertThrows(
        InvalidPagedRequestException.class,
        () -> PagedRequestJooqTranslator.translate(request, columnsByColId, null));
  }
}
