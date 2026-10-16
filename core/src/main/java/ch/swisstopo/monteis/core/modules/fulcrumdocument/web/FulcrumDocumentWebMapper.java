package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumDocumentResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumEntryResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumSyncStatusResponseDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface FulcrumDocumentWebMapper {

  @Mapping(target = ".", source = "citation")
  @Mapping(target = "document", source = "document")
  @Mapping(target = "availableInFulcrum", expression = "java(domain.isAvailableInFulcrum())")
  FulcrumEntryResponseDto toDto(FulcrumEntry domain);

  List<FulcrumEntryResponseDto> toDtos(List<FulcrumEntry> domains);

  @Mapping(target = "fileName", source = "metadata.fileName")
  @Mapping(target = "contentType", source = "metadata.contentType")
  @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
  @Mapping(
      target = "viewable",
      source = "metadata.contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  FulcrumDocumentResponseDto toDocumentDto(FulcrumDocument domain);

  FulcrumSyncStatusResponseDto toStatusDto(FulcrumSyncStatus domain);
}
