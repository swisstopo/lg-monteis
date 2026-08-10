package ch.swisstopo.monteis.core.infrastructure.query;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Turns a {@link RawPagedRequest} bound from GET query parameters into a {@link PagedRequest}. */
@Component
public class PagedRequestParser {
  private static final int MAX_PAGE_SIZE = 500;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public PagedRequest parse(RawPagedRequest raw) {
    if (raw.startRow() > raw.endRow()) {
      throw new InvalidPagedRequestException(
          "startRow (%d) must not be greater than endRow (%d)"
              .formatted(raw.startRow(), raw.endRow()));
    }
    int pageSize = raw.endRow() - raw.startRow();
    if (pageSize > MAX_PAGE_SIZE) {
      throw new InvalidPagedRequestException(
          "Requested page size (%d) exceeds the maximum of %d".formatted(pageSize, MAX_PAGE_SIZE));
    }

    List<SortModelItem> sortModel =
        raw.sortModel() == null || raw.sortModel().isBlank()
            ? List.of()
            : readValue(raw.sortModel(), new TypeReference<>() {});
    Map<String, FilterModelItem> filterModel =
        raw.filterModel() == null || raw.filterModel().isBlank()
            ? Map.of()
            : readValue(raw.filterModel(), new TypeReference<>() {});
    return new PagedRequest(raw.startRow(), raw.endRow(), sortModel, filterModel);
  }

  private <T> T readValue(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new InvalidPagedRequestException("Invalid sort/filter model: " + e.getMessage(), e);
    }
  }
}
