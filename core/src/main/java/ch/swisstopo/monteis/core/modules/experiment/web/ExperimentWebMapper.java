package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.LocalDate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExperimentWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "sensorCount", ignore = true)
  Experiment toDomain(WriteExperimentDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "status", expression = "java(domain.getStatus(today))")
  ExperimentResponseDto toDto(Experiment domain, @Context LocalDate today);

  // --- Paged Outbound Domain -> Paged API Serialization DTO Mappings ---
  PagedResult<ExperimentResponseDto> toPagedDto(
      PagedResult<Experiment> pagedResult, @Context LocalDate today);
}
