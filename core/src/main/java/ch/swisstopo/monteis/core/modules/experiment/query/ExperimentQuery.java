package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;

/**
 * Query-side interface for Experiment-related read operations.
 * <p>
 * This interface implements the CQRS read flow. It explicitly bypasses the
 * domain model and MapStruct mappers, using jOOQ to project database records
 * directly into UI-optimized Data Transfer Objects (DTOs).
 * <p>
 * Use this interface for HTTP GET endpoints, dashboards, or any data aggregations
 * where business invariant validation is not required.
 */
public interface ExperimentQuery {
  /**
   * Retrieves an experiment by its ID, projected straight into a {@link ExperimentResponseDto}.
   *
   * @param id the ID of the experiment to retrieve
   * @return the experiment response DTO
   */
  ExperimentResponseDto getById(Long id);
}
