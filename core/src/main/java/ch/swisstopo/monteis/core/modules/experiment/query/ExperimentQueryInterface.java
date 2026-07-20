package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;

public interface ExperimentQueryInterface {
  ReadExperimentDetailsDto getExperimentDetails(Long experimentID);
}
