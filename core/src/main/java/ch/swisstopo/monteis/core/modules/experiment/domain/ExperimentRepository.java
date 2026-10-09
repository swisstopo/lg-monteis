package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Command-side repository for the {@link Experiment} aggregate root.
 * <p>
 * This interface is part of the strict Domain-Driven Design (DDD) write flow.
 * It is exclusively responsible for state-mutating operations (e.g., create, update)
 * and domain reconstruction. It works solely with rich domain objects to ensure
 * business invariants are protected.
 * <p>
 */
public interface ExperimentRepository {
  /**
   * Persists a new {@link Experiment} entity.
   *
   * @param experiment the experiment to persist
   * @return the persisted experiment instance including DB managed state such as version
   */
  Experiment create(Experiment experiment);

  /**
   * Updates an existing {@link Experiment} entity.
   *
   * @param experiment the experiment to update
   * @return the updated experiment instance including DB managed state such as version
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     experiment does not exist or is hidden from the caller
   */
  Experiment update(Experiment experiment);

  Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds);

  /**
   * Retrieves all unaudited experiments
   *
   * @return a stream of all experiments which are not yet audited
   */
  Stream<Experiment> streamUnauditedExperiments();

  /**
   * Retrieves an {@link Experiment} by its ID
   *
   * @param id the ID of the experiment to retrieve
   * @return the experiment
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     experiment does not exist or is hidden from the caller
   */
  Experiment getById(UUID id);

  void requireVisible(UUID id);

  /**
   * Retrieves a page of {@link Experiment}s
   *
   * @param request the requested page, together with an optional sort/filter model
   * @return the requested page of experiments together with the total row count
   */
  PagedResult<Experiment> getExperiments(PagedRequest request);

  /**
   * Retrieves all {@link Experiment}s (unpaged), sorted alphabetically by name.
   * Intended for lightweight lookup/autocomplete UIs (e.g. picking a sensor's main
   * experiment). Subject to the same row-level security as {@link #getExperiments}.
   *
   * @return all experiments visible to the current user
   */
  List<Experiment> findAll();
}
