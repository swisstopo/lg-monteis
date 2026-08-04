package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExperimentWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  Experiment toDomain(WriteExperimentDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  ExperimentResponseDto toDto(Experiment domain);
}
