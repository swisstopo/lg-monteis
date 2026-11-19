package ch.swisstopo.monteis.core.modules.notification.web;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientDto;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientsResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RecipientWebMapper {

  @Mapping(target = "recipients", source = "users")
  RecipientsResponseDto toDto(Recipients recipients);

  RecipientDto toDto(DirectoryUser user);
}
