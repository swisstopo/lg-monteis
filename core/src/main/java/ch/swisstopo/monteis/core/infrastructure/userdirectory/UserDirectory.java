package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserDirectory {

  Map<UUID, Pis> pisForReading(Collection<UUID> experimentIds);

  Pis pisForWriting(UUID experimentId);
}
