package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// a row from Fulcrum's literature table and the file stored for it make a Fulcrum Document
@Mapper(componentModel = "spring")
public interface FulcrumLiteratureMapper {

  @Mapping(target = "id", source = "entry.recordId")
  @Mapping(target = "file", source = "file")
  @Mapping(target = "removedAt", ignore = true)
  FulcrumDocument toDocument(FulcrumLiteratureEntry entry, FulcrumDocumentFile file);
}
