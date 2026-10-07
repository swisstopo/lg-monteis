package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentWithOwners;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.LocalDate;
import java.util.List;
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

  default ExperimentResponseDto toDto(
      ExperimentWithOwners experimentWithOwners, @Context LocalDate today) {
    return toDto(experimentWithOwners.experiment(), experimentWithOwners.owners(), today);
  }

  // --- Paged Outbound Domain -> Paged API Serialization DTO Mappings ---
  default PagedResult<ExperimentResponseDto> toPagedDto(
      PagedResult<ExperimentWithOwners> page, LocalDate today) {
    return new PagedResult<>(
        page.rows().stream().map(row -> toDto(row, today)).toList(), page.totalCount());
  }
}
