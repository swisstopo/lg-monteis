package ch.swisstopo.monteis.core.infrastructure.query;

import java.util.Set;

public record SetFilterModel(String filterType, Set<String> values) implements FilterModelItem {}
