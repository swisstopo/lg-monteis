package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumDocumentsRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FulcrumDocumentJooqMapper {

  @Mapping(target = "file", source = ".")
  FulcrumDocument toDocument(FulcrumDocumentsRecord documentRecord);

  @Mapping(target = "metadata", source = ".")
  FulcrumDocumentFile toFile(FulcrumDocumentsRecord documentRecord);

  DocumentMetadata toMetadata(FulcrumDocumentsRecord documentRecord);

  default List<String> keywordsOf(String[] keywords) {
    return keywords == null ? List.of() : List.of(keywords);
  }
}
