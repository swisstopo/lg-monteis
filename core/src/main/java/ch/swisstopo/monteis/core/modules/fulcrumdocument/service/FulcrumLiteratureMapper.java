package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FulcrumLiteratureMapper {

  @Mapping(target = "id", source = "row.recordId")
  @Mapping(target = "file", source = "file")
  @Mapping(target = "removedAt", ignore = true)
  FulcrumDocument toDocument(FulcrumLiteratureRow row, FulcrumDocumentFile file);
}
