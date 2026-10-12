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
  @Mapping(target = "ownersStatus", source = "owners.status")
  ExperimentResponseDto toDto(Experiment domain, VisibleOwners owners, @Context LocalDate today);

  default ExperimentResponseDto toDto(Experiment domain, @Context LocalDate today) {
    // a sensor without main experiment, mapstruct only returns null when every source is null
    if (domain == null) {
      return null;
    }
    // only the sensor's main experiment comes here, its owners are not loaded, NONE would claim
    // it has none
    return toDto(domain, null, today);
  }

  ExperimentOwnerDto toOwnerDto(DirectoryUser user);

  List<ExperimentOwnerDto> toOwnerDtos(List<DirectoryUser> users);

  default List<ExperimentResponseDto> toDtos(
      List<Experiment> experiments, Map<UUID, VisibleOwners> owners, LocalDate today) {
    return experiments.stream()
        .map(experiment -> toDto(experiment, owners.get(experiment.getId()), today))
        .toList();
  }

  // --- Paged Outbound Domain -> Paged API Serialization DTO Mappings ---
  default PagedResult<ExperimentResponseDto> toPagedDto(
      PagedResult<Experiment> page, Map<UUID, VisibleOwners> owners, LocalDate today) {
    return new PagedResult<>(toDtos(page.rows(), owners, today), page.totalCount());
  }
}
