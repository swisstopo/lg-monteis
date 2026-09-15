package ch.swisstopo.monteis.core.infrastructure.query;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link RawPagedRequest}/{@link RawExportRequest} bound from GET query parameters into a
 * {@link PagedRequest}.
 */
@Component
public class PagedRequestParser {
  private static final int MAX_PAGE_SIZE = 500;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final int csvExportMaxRows;

  public PagedRequestParser(@Value("${app.export.csv.max-rows:50000}") int csvExportMaxRows) {
    this.csvExportMaxRows = csvExportMaxRows;
  }

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

    return new PagedRequest(
        raw.startRow(),
        raw.endRow(),
        parseSortModel(raw.sortModel()),
        parseFilterModel(raw.filterModel()));
  }

  /**
   * Parses a CSV export request: same sortModel/filterModel shape as {@link #parse}, but capped
   * at the configured export row limit instead of the grid's page-size cap - an export has no
   * client-driven startRow/endRow, so it always starts at 0.
   */
  public PagedRequest parseForExport(RawExportRequest raw) {
    return new PagedRequest(
        0, csvExportMaxRows, parseSortModel(raw.sortModel()), parseFilterModel(raw.filterModel()));
  }

  private List<SortModelItem> parseSortModel(String sortModel) {
    return sortModel == null || sortModel.isBlank()
        ? List.of()
        : readValue(sortModel, new TypeReference<>() {});
  }

  private Map<String, FilterModelItem> parseFilterModel(String filterModel) {
    return filterModel == null || filterModel.isBlank()
        ? Map.of()
        : readValue(filterModel, new TypeReference<>() {});
  }

  private <T> T readValue(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new InvalidPagedRequestException("Invalid sort/filter model: " + e.getMessage(), e);
    }
  }
}
