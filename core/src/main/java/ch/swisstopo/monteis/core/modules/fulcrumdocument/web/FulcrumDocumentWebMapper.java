package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryWithDocuments;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumDocumentResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumEntryResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumSyncRunResponseDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface FulcrumDocumentWebMapper {

  @Mapping(target = ".", source = "entry")
  @Mapping(target = "documents", source = "documents")
  @Mapping(
      target = "availableInFulcrum",
      expression = "java(domain.entry().isAvailableInFulcrum())")
  FulcrumEntryResponseDto toDto(FulcrumEntryWithDocuments domain);

  List<FulcrumEntryResponseDto> toDtos(List<FulcrumEntryWithDocuments> domains);

  @Mapping(target = "fileName", source = "metadata.fileName")
  @Mapping(target = "contentType", source = "metadata.contentType")
  @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
  @Mapping(
      target = "viewable",
      source = "metadata.contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  @Mapping(target = "availableInFulcrum", expression = "java(domain.isAvailableInFulcrum())")
  FulcrumDocumentResponseDto toDocumentDto(FulcrumDocument domain);

  FulcrumSyncRunResponseDto toRunDto(FulcrumSyncRun domain);
}
