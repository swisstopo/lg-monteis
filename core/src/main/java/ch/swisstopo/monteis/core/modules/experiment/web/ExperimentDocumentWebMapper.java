package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.DocumentResponseDto;
import ch.swisstopo.monteis.core.infrastructure.api.DocumentUploadMapper;
import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface ExperimentDocumentWebMapper extends DocumentUploadMapper {

  @Mapping(target = "fileName", source = "metadata.fileName")
  @Mapping(target = "contentType", source = "metadata.contentType")
  @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
  @Mapping(
      target = "viewable",
      source = "metadata.contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  DocumentResponseDto toDto(ExperimentDocument domain);

  List<DocumentResponseDto> toDtos(List<ExperimentDocument> domains);
}
