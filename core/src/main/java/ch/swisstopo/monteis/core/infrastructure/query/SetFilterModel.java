package ch.swisstopo.monteis.core.infrastructure.query;

import java.util.List;

public record SetFilterModel(String filterType, List<String> values) implements FilterModelItem {}
