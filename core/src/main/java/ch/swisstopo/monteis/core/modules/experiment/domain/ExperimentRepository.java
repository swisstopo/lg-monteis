package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.util.stream.Stream;

/**
 * Command-side repository for the {@link Experiment} aggregate root.
 * <p>
 * This interface is part of the strict Domain-Driven Design (DDD) write flow.
 * It is exclusively responsible for state-mutating operations (e.g., create, update)
 * and domain reconstruction. It works solely with rich domain objects to ensure
 * business invariants are protected.
 * <p>
 * Do not add UI-specific read methods here. For read-only operations that return
 * DTOs, see {@link ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQuery}.
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
   */
  Experiment update(Experiment experiment);

  /**
   * Retrieves all unaudited experiments
   *
   * @return a stream of all experiments which are not yet audited
   */
  Stream<Experiment> streamUnauditedExperiments();
}
