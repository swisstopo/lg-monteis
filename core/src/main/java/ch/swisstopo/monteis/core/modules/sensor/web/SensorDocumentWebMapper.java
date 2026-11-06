package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.api.DocumentResponseDto;
import ch.swisstopo.monteis.core.infrastructure.api.DocumentUploadMapper;
import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ViewableContentTypes.class)
public interface SensorDocumentWebMapper extends DocumentUploadMapper {

  @Mapping(target = "fileName", source = "metadata.fileName")
  @Mapping(target = "contentType", source = "metadata.contentType")
  @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
  @Mapping(
      target = "viewable",
      source = "metadata.contentType",
      qualifiedByName = ViewableContentTypes.VIEWABLE)
  DocumentResponseDto toDto(SensorDocument domain);

  List<DocumentResponseDto> toDtos(List<SensorDocument> domains);
}
