package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
  private final ExperimentRepository repository;

  public ExperimentService(ExperimentRepository repository) {
    this.repository = repository;
  }

  @AuditChanges
  public Experiment createExperiment(Experiment experiment) {
    return repository.create(experiment);
  }

  @AuditChanges
  public Experiment updateExperiment(Experiment experiment) {
    return repository.update(experiment);
  }

  public Experiment getById(Long experimentId) {
    return repository.getById(experimentId);
  }

  public PagedResult<Experiment> getExperiments(PagedRequest pagedRequest) {
    return repository.getExperiments(pagedRequest);
  }
}
