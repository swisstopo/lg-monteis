package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.organisation.domain.OrganisationRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
  private final ExperimentRepository repository;
  private final OrganisationRepository organisationRepository;

  public ExperimentService(
      ExperimentRepository repository, OrganisationRepository organisationRepository) {
    this.repository = repository;
    this.organisationRepository = organisationRepository;
  }

  @AuditChanges
  public Experiment createExperiment(Experiment experiment) {
    rejectUnknownOrganisations(experiment);
    return repository.create(experiment);
  }

  @AuditChanges
  public Experiment updateExperiment(Experiment experiment) {
    rejectUnknownOrganisations(experiment);
    return repository.update(experiment);
  }

  private void rejectUnknownOrganisations(Experiment experiment) {
    Set<UUID> existing =
        organisationRepository.findExistingIdsAmong(experiment.getOrganisationIds());
    List<UUID> unknown =
        experiment.getOrganisationIds().stream().filter(id -> !existing.contains(id)).toList();
    if (!unknown.isEmpty()) {
      throw new FieldBusinessValidationException(
          "organisationIds", unknown, "validation.organisation.unknown", Map.of());
    }
  }

  public Experiment getById(UUID experimentId) {
    return repository.getById(experimentId);
  }

  public PagedResult<Experiment> getExperiments(PagedRequest pagedRequest) {
    return repository.getExperiments(pagedRequest);
  }

  public List<Experiment> findAllExperiments() {
    return repository.findAll();
  }
}
