package ch.swisstopo.monteis.core.modules.organisation.domain;

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
   * Deletes an organisation. Experiments lose it as well, the database cascades the link rows.
   *
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     organisation does not exist
   */
  void delete(UUID id);

  /** Retrieves an organisation by id, empty if it does not exist. */
  Optional<Organisation> findById(UUID id);

  /** Retrieves all organisations, sorted alphabetically by name. */
  List<Organisation> findAll();
}
