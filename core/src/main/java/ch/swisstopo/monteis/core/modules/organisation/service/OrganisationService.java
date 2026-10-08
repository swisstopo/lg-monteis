package ch.swisstopo.monteis.core.modules.organisation.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.domain.OrganisationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrganisationService {
  private final OrganisationRepository repository;

  public OrganisationService(OrganisationRepository repository) {
    this.repository = repository;
  }

  public Organisation createOrganisation(Organisation organisation) {
    return repository.create(organisation);
  }

  public void deleteOrganisation(UUID id) {
    repository.delete(id);
  }

  public Organisation getOrganisation(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ObjectNotFoundException(Organisation.class));
  }

  public List<Organisation> findAllOrganisations() {
    return repository.findAll();
  }
}
