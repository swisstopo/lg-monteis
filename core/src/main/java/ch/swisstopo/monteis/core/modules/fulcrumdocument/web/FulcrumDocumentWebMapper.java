package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumDocumentFileResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumDocumentResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumSyncRunResponseDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface FulcrumDocumentWebMapper {

  @Mapping(target = "availableInFulcrum", expression = "java(domain.isAvailableInFulcrum())")
  FulcrumDocumentResponseDto toDto(FulcrumDocument domain);

  List<FulcrumDocumentResponseDto> toDtos(List<FulcrumDocument> domains);

  @Mapping(
      target = "viewable",
      source = "contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  @Mapping(target = "stored", expression = "java(domain.isStored())")
  @Mapping(target = "availableInFulcrum", expression = "java(domain.isAvailableInFulcrum())")
  FulcrumDocumentFileResponseDto toFileDto(FulcrumDocumentFile domain);

  FulcrumSyncRunResponseDto toRunDto(FulcrumSyncRun domain);
}
