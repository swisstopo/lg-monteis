package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserDirectory {

  Map<UUID, Pis> pisForReading(Collection<UUID> experimentIds);

  default Pis pisForReading(UUID experimentId) {
    return pisForReading(List.of(experimentId)).get(experimentId);
  }

  Pis pisForWriting(UUID experimentId);
}
