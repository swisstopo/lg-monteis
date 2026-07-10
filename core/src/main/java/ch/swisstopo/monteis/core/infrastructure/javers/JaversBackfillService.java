package ch.swisstopo.monteis.core.infrastructure.javers;

import ch.swisstopo.monteis.core.modules.demo.jooq.DemoRepository;
import org.javers.core.Javers;
import org.springframework.stereotype.Component;

@Component
public class JaversBackfillService {

  private final Javers javers;
  private final DemoRepository demoRepository;

  public JaversBackfillService(Javers javers, DemoRepository demoRepository) {
    this.javers = javers;
    this.demoRepository = demoRepository;
  }

  //  @EventListener(ApplicationReadyEvent.class)
  //  @Transactional
  //  public void backfillMissingSnapshots() {
  //    try (var unauditedSensorsStream = demoRepository.streamUnauditedSensors()) {
  //
  //      unauditedSensorsStream.forEach(
  //          sensor -> {
  //            javers.commit("SYSTEM_SEEDER", sensor);
  //          });
  //    }
  //  }
}
