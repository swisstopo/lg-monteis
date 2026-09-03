package ch.swisstopo.monteis.core.infrastructure.jooq;

import static org.junit.jupiter.api.Assertions.*;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.query.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PagedRequestJooqTranslatorTest {

  private static final Field<String> nameField = DSL.field("name", String.class);
  private static final Field<Double> ageField = DSL.field("age", Double.class);
  private static final Field<LocalDate> startField = DSL.field("start", LocalDate.class);
  private static final Map<String, Field<?>> columns =
      Map.of("name", nameField, "age", ageField, "start", startField);

  @Test
  void should_useDefaultSort_when_emptyRequestWithDefaultSort() {
    // given
    PagedRequest request = new PagedRequest(0, 10, List.of(), Map.of());
    var defaultSort = nameField.asc();

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, defaultSort);

    // then
    assertEquals(DSL.noCondition(), criteria.condition());
    assertEquals(1, criteria.sortFields().size());
    assertTrue(criteria.sortFields().contains(defaultSort));
  }

  @Test
  void should_translateCorrectly_when_withSortModel() {
    // given
    PagedRequest request =
        new PagedRequest(0, 10, List.of(new SortModelItem("age", SortDirection.DESC)), Map.of());

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, nameField.asc());

    // then
    assertEquals(1, criteria.sortFields().size());
    assertEquals(ageField.desc().toString(), criteria.sortFields().iterator().next().toString());
  }

  static Stream<Arguments> textFilterProvider() {
    Field<String> f = nameField.cast(String.class);
    return Stream.of(
        Arguments.of("contains", "smith", null, f.containsIgnoreCase("smith")),
        Arguments.of("notContains", "smith", null, f.notContainsIgnoreCase("smith")),
        Arguments.of("equals", "smith", null, f.equalIgnoreCase("smith")),
        Arguments.of("notEqual", "smith", null, f.notEqualIgnoreCase("smith")),
        Arguments.of("startsWith", "smith", null, f.startsWithIgnoreCase("smith")),
        Arguments.of("endsWith", "smith", null, f.endsWithIgnoreCase("smith")),
        Arguments.of("blank", null, null, f.isNull().or(f.eq(""))),
        Arguments.of("notBlank", null, null, f.isNotNull().and(f.ne(""))));
  }

  @ParameterizedTest(name = "text filter type ''{0}'' translates correctly")
  @MethodSource("textFilterProvider")
  void should_translateCorrectly_when_textFilterIsProvided(
      String type, String filter, String filterTo, Condition expectedCondition) {
    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("name", new TextFilterModel(type, filter, filterTo)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    Condition expected = DSL.noCondition().and(expectedCondition);
    assertEquals(expected.toString(), criteria.condition().toString());
  }

  static Stream<Arguments> numberFilterProvider() {
    Field<Double> f = ageField.cast(Double.class);
    return Stream.of(
        Arguments.of("equals", 20.0, null, f.eq(20.0)),
        Arguments.of("notEqual", 20.0, null, f.ne(20.0)),
        Arguments.of("lessThan", 20.0, null, f.lt(20.0)),
        Arguments.of("lessThanOrEqual", 20.0, null, f.le(20.0)),
        Arguments.of("greaterThan", 20.0, null, f.gt(20.0)),
        Arguments.of("greaterThanOrEqual", 20.0, null, f.ge(20.0)),
        Arguments.of("inRange", 20.0, 30.0, f.between(20.0, 30.0)),
        Arguments.of("blank", null, null, f.isNull()),
        Arguments.of("notBlank", null, null, f.isNotNull()));
  }

  @ParameterizedTest(name = "number filter type ''{0}'' translates correctly")
  @MethodSource("numberFilterProvider")
  void should_translateCorrectly_when_numberFilterIsProvided(
      String type, Double filter, Double filterTo, Condition expectedCondition) {
    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("age", new NumberFilterModel(type, filter, filterTo)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    Condition expected = DSL.noCondition().and(expectedCondition);
    assertEquals(expected.toString(), criteria.condition().toString());
  }

  static Stream<Arguments> dateFilterProvider() {
    Field<LocalDate> f = startField.cast(LocalDate.class);

    LocalDate d1 = LocalDate.parse("2026-01-01");
    LocalDate d2 = LocalDate.parse("2026-12-31");

    return Stream.of(
        Arguments.of("equals", "2026-01-01", null, f.eq(d1)),
        Arguments.of("notEqual", "2026-01-01", null, f.ne(d1)),
        Arguments.of("lessThan", "2026-01-01", null, f.lt(d1)),
        Arguments.of("lessThanOrEqual", "2026-01-01", null, f.le(d1)),
        Arguments.of("greaterThan", "2026-01-01", null, f.gt(d1)),
        Arguments.of("greaterThanOrEqual", "2026-01-01", null, f.ge(d1)),
        Arguments.of("inRange", "2026-01-01", "2026-12-31", f.between(d1, d2)),
        Arguments.of("blank", null, null, f.isNull()),
        Arguments.of("notBlank", null, null, f.isNotNull()));
  }

  @ParameterizedTest(name = "date filter type ''{0}'' translates correctly")
  @MethodSource("dateFilterProvider")
  void should_translateCorrectly_when_dateFilterIsProvided(
      String type, String dateFrom, String dateTo, Condition expectedCondition) {

    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("start", new DateFilterModel(type, dateFrom, dateTo)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    Condition expected = DSL.noCondition().and(expectedCondition);
    assertEquals(expected.toString(), criteria.condition().toString());
  }

  static Stream<Arguments> setFilterProvider() {
    Field<String> f = nameField.cast(String.class);
    return Stream.of(
        Arguments.of("null values", null, DSL.falseCondition()),
        Arguments.of("empty values", Set.of(), DSL.falseCondition()),
        Arguments.of(
            "valid values", Set.of("ACTIVE", "HISTORIC"), f.in(Set.of("ACTIVE", "HISTORIC"))));
  }

  @ParameterizedTest(name = "set filter with {0} translates correctly")
  @MethodSource("setFilterProvider")
  void should_translateCorrectly_when_setFilterIsProvided(
      String description, Set<String> values, Condition expectedCondition) {

    // given
    PagedRequest request =
        new PagedRequest(0, 10, List.of(), Map.of("name", new SetFilterModel("Set", values)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    Condition expected = DSL.noCondition().and(expectedCondition);
    assertEquals(expected.toString(), criteria.condition().toString());
  }

  @Test
  void should_throwException_when_invalidTextFilterTypeProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("name", new TextFilterModel("invalidType", "smith", null)));

    // when
    InvalidPagedRequestException exception =
        assertThrows(
            InvalidPagedRequestException.class,
            () -> PagedRequestJooqTranslator.translate(request, columns, null));

    // then
    assertTrue(exception.getMessage().contains("Unsupported text filter type: invalidType"));
  }

  @Test
  void should_throwException_when_invalidNumberFilterTypeProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(), Map.of("age", new NumberFilterModel("invalidType", 20.0, null)));

    // when
    InvalidPagedRequestException exception =
        assertThrows(
            InvalidPagedRequestException.class,
            () -> PagedRequestJooqTranslator.translate(request, columns, null));

    // then
    assertTrue(exception.getMessage().contains("Unsupported number filter type: invalidType"));
  }

  @Test
  void should_throwException_when_invalidDateFilterTypeProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0,
            10,
            List.of(),
            Map.of("start", new DateFilterModel("invalidType", "2026-01-01", null)));

    // when
    InvalidPagedRequestException exception =
        assertThrows(
            InvalidPagedRequestException.class,
            () -> PagedRequestJooqTranslator.translate(request, columns, null));

    // then
    assertTrue(exception.getMessage().contains("Unsupported date filter type: invalidType"));
  }

  @Test
  void should_throwException_when_unknownColumnProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0, 10, List.of(new SortModelItem("unknownCol", SortDirection.ASC)), Map.of());

    // when
    InvalidPagedRequestException exception =
        assertThrows(
            InvalidPagedRequestException.class,
            () -> PagedRequestJooqTranslator.translate(request, columns, null));

    // then
    assertTrue(exception.getMessage().contains("Unknown sortable/filterable column: unknownCol"));
  }

  @Test
  void should_translateCorrectly_when_equalsFilterAndAscSortingProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0,
            10,
            List.of(new SortModelItem("name", SortDirection.ASC)),
            Map.of("name", new TextFilterModel("equals", "john", null)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    assertEquals(1, criteria.sortFields().size());
    assertEquals(nameField.asc().toString(), criteria.sortFields().iterator().next().toString());

    Condition expectedCondition =
        DSL.noCondition().and(nameField.cast(String.class).equalIgnoreCase("john"));
    assertEquals(expectedCondition.toString(), criteria.condition().toString());
  }

  @Test
  void should_translateCorrectly_when_containsFilterAndAscSortingProvided() {
    // given
    PagedRequest request =
        new PagedRequest(
            0,
            10,
            List.of(new SortModelItem("name", SortDirection.ASC)),
            Map.of("name", new TextFilterModel("contains", "john", null)));

    // when
    var criteria = PagedRequestJooqTranslator.translate(request, columns, null);

    // then
    assertEquals(1, criteria.sortFields().size());
    assertEquals(nameField.asc().toString(), criteria.sortFields().iterator().next().toString());

    Condition expectedCondition =
        DSL.noCondition().and(nameField.cast(String.class).containsIgnoreCase("john"));
    assertEquals(expectedCondition.toString(), criteria.condition().toString());
  }
}
