package ch.swisstopo.monteis.core.infrastructure.query;

import java.util.List;

public record PagedResult<T>(List<T> rows, int totalCount) {}
