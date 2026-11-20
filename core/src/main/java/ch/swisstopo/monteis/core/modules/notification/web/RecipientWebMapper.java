package ch.swisstopo.monteis.core.modules.notification.web;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUserWebMapper;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientCandidate;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientCandidateDto;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientsResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = DirectoryUserWebMapper.class)
public interface RecipientWebMapper {

  RecipientsResponseDto toDto(Recipients recipients);

  RecipientCandidateDto toDto(RecipientCandidate candidate);
}
