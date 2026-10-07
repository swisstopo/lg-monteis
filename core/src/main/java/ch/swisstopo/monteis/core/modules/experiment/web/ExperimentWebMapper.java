package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExperimentWebMapper {
  // --- Inbound API DTO -> Core Rich Domain Object Mappings ---
  @Mapping(target = "sensorCount", ignore = true)
  @Mapping(target = "ownerIds", ignore = true)
  Experiment toDomain(WriteExperimentDto dto);

  // --- Outbound Domain -> API Serialization DTO Mappings ---
  @Mapping(target = "status", expression = "java(domain.getStatus(today))")
  @Mapping(target = "owners", source = "owners.users")
  @Mapping(target = "ownersUnavailable", source = "owners.unavailable")
  ExperimentResponseDto toDto(Experiment domain, VisibleOwners owners, @Context LocalDate today);

  /** For an experiment nested in another resource (a sensor's main experiment), without owners. */
  default ExperimentResponseDto toDto(Experiment domain, @Context LocalDate today) {
    return toDto(domain, VisibleOwners.NONE, today);
  }

  ExperimentOwnerDto toOwnerDto(DirectoryUser user);

  List<ExperimentOwnerDto> toOwnerDtos(List<DirectoryUser> users);

  // --- Paged Outbound Domain -> Paged API Serialization DTO Mappings ---
  default PagedResult<ExperimentResponseDto> toPagedDto(
      PagedResult<Experiment> pagedResult,
      Map<UUID, VisibleOwners> ownersByExperiment,
      LocalDate today) {
    return new PagedResult<>(
        pagedResult.rows().stream()
            .map(
                e ->
                    toDto(e, ownersByExperiment.getOrDefault(e.getId(), VisibleOwners.NONE), today))
            .toList(),
        pagedResult.totalCount());
  }
}
