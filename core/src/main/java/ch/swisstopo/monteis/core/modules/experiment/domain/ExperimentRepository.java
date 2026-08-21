package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
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
   */
  Experiment update(Experiment experiment);

  /**
   * Retrieves all unaudited experiments
   *
   * @return a stream of all experiments which are not yet audited
   */
  Stream<Experiment> streamUnauditedExperiments();

  /**
   * Retrieves an experiment by its ID, projected straight into a {@link ExperimentResponseDto}.
   *
   * @param id the ID of the experiment to retrieve
   * @return the experiment response DTO
   */
  Experiment getById(Long id);
}
