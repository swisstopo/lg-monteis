package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumEntriesRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumCitation;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FulcrumEntryJooqMapper {

  @Mapping(target = "citation", source = ".")
  @Mapping(target = "document", source = ".")
  FulcrumEntry toEntry(FulcrumEntriesRecord entryRecord);

  FulcrumCitation toCitation(FulcrumEntriesRecord entryRecord);

  @Mapping(target = "id", source = "attachmentId")
  @Mapping(target = "metadata", source = ".")
  FulcrumDocument toDocument(FulcrumEntriesRecord entryRecord);

  DocumentMetadata toMetadata(FulcrumEntriesRecord entryRecord);

  default List<String> keywordsOf(String[] keywords) {
    return keywords == null ? List.of() : List.of(keywords);
  }
}
