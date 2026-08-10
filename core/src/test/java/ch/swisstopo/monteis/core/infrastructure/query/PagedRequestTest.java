package ch.swisstopo.monteis.core.infrastructure.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PagedRequestTest {

  @Test
  void should_initializeEmptyCollections_when_nullModelsProvided() {
    // given
    int startRow = 0;
    int endRow = 10;

    // when
    PagedRequest request = new PagedRequest(startRow, endRow, null, null);

    // then
    assertTrue(request.sortModel().isEmpty());
    assertTrue(request.filterModel().isEmpty());
    assertEquals(0, request.startRow());
    assertEquals(10, request.endRow());
  }

  @Test
  void should_calculateCorrectLimitAndOffset_when_validBoundsProvided() {
    // given
    PagedRequest request = new PagedRequest(50, 100, List.of(), Map.of());

    // when
    int offset = request.offset();
    int limit = request.limit();

    // then
    assertEquals(50, offset);
    assertEquals(50, limit);
  }

  @Test
  void should_returnZeroLimit_when_startRowIsGreaterThanEndRow() {
    // given
    PagedRequest request = new PagedRequest(100, 50, List.of(), Map.of());

    // when
    int limit = request.limit();

    // then
    assertEquals(0, limit);
  }
}
