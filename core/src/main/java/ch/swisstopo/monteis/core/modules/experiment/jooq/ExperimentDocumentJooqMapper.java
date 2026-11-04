package ch.swisstopo.monteis.core.modules.experiment.jooq;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentDocumentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExperimentDocumentJooqMapper {

  @Mapping(target = "metadata", source = ".")
  ExperimentDocument toDomain(ExperimentDocumentsRecord documentRecord);

  DocumentMetadata toMetadata(ExperimentDocumentsRecord documentRecord);
}
