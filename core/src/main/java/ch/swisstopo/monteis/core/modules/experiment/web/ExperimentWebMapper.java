package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExperimentWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "owner", ignore = true)
  Experiment toDomain(WriteExperimentDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "sensorCount", ignore = true)
  ExperimentResponseDto toDto(Experiment domain);
}
