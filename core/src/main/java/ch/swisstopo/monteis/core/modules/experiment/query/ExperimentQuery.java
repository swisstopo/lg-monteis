package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;

public interface ExperimentQuery {
  ExperimentResponseDto getExperimentDetails(Long experimentID);
}
