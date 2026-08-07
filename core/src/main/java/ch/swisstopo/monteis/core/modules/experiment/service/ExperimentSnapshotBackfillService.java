package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!openapi")
public class ExperimentSnapshotBackfillService {
  private final ExperimentRepository repository;
  private final Javers javers;

  public ExperimentSnapshotBackfillService(ExperimentRepository repository, Javers javers) {
    this.repository = repository;
    this.javers = javers;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void backfillMissingSnapshots() {
    // No HTTP request/JWT exists at startup, so this explicitly opts into elevated DB
    // access rather than relying on the fail-closed default for an unbound SecurityContextHolder.
    SystemSecurityContext.runAsSystem(
        () -> {
          try (Stream<Experiment> unauditedExperimentsStream =
              repository.streamUnauditedExperiments()) {
            unauditedExperimentsStream.forEach(
                experiment -> javers.commit("SYSTEM_SEEDER", experiment));
          }
        });
  }
}
