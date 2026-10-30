package ch.swisstopo.monteis.core.modules.sensor.jooq;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorDocumentsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SensorDocumentJooqMapper {

  @Mapping(target = "metadata", source = ".")
  SensorDocument toDomain(SensorDocumentsRecord documentRecord);

  DocumentMetadata toMetadata(SensorDocumentsRecord documentRecord);
}
