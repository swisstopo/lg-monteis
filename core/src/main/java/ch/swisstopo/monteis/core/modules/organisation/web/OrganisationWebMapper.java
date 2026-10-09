package ch.swisstopo.monteis.core.modules.organisation.web;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.inbound.WriteOrganisationDto;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.outbound.OrganisationResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganisationWebMapper {

  @Mapping(target = "id", ignore = true)
  Organisation toDomain(WriteOrganisationDto dto);

  OrganisationResponseDto toDto(Organisation domain);

  PagedResult<OrganisationResponseDto> toPagedDto(PagedResult<Organisation> pagedResult);
}
