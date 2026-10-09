package ch.swisstopo.monteis.core.modules.organisation.domain;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository {
  /**
   * Persists a new {@link Organisation}.
   *
   * @return the persisted organisation including its generated id
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException if
   *     an organisation with the same name exists, ignoring case
   */
  Organisation create(Organisation organisation);

  /**
   * Renames an organisation.
   *
   * @return the updated organisation
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     organisation does not exist
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException if
   *     another organisation with the same name exists, ignoring case
   */
  Organisation update(Organisation organisation);

  /** Retrieves an organisation by id, empty if it does not exist. */
  Optional<Organisation> findById(UUID id);

  /** Retrieves all organisations, sorted alphabetically by name. */
  List<Organisation> findAll();

  /**
   * Retrieves a page of organisations with optional sorting/filtering. Sorted by id when the
   * request has no sort model, to keep offset-based paging stable.
   */
  PagedResult<Organisation> getOrganisations(PagedRequest request);
}
