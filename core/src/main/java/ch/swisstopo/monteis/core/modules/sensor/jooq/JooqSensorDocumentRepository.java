package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_DOCUMENTS;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentRepository;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqSensorDocumentRepository implements SensorDocumentRepository {

  private final DSLContext dsl;
  private final SensorDocumentJooqMapper mapper;

  public JooqSensorDocumentRepository(DSLContext dsl, SensorDocumentJooqMapper mapper) {
    this.dsl = dsl;
    this.mapper = mapper;
  }

  // the upload needs the id for the S3 key before the insert, the db still generates it
  @Override
  @Transactional(readOnly = true)
  public UUID nextId() {
    return dsl.select(DSL.function("uuidv7", UUID.class)).fetchSingle().value1();
  }

  @Override
  @Transactional
  public SensorDocument create(
      UUID id, UUID sensorId, DocumentMetadata metadata, String uploadedBy) {
    return dsl.insertInto(SENSOR_DOCUMENTS)
        .set(SENSOR_DOCUMENTS.ID, id)
        .set(SENSOR_DOCUMENTS.SENSOR_ID, sensorId)
        .set(SENSOR_DOCUMENTS.FILE_NAME, metadata.fileName())
        .set(SENSOR_DOCUMENTS.CONTENT_TYPE, metadata.contentType())
        .set(SENSOR_DOCUMENTS.SIZE_BYTES, metadata.sizeBytes())
        .set(SENSOR_DOCUMENTS.UPLOADED_BY, uploadedBy)
        .returning()
        .fetchSingle(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SensorDocument> findBySensorId(UUID sensorId) {
    return dsl.selectFrom(SENSOR_DOCUMENTS)
        .where(SENSOR_DOCUMENTS.SENSOR_ID.eq(sensorId))
        .orderBy(SENSOR_DOCUMENTS.UPLOADED_AT.desc(), SENSOR_DOCUMENTS.ID.desc())
        .fetch(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public SensorDocument getById(UUID sensorId, UUID documentId) {
    return dsl.selectFrom(SENSOR_DOCUMENTS)
        .where(SENSOR_DOCUMENTS.ID.eq(documentId))
        .and(SENSOR_DOCUMENTS.SENSOR_ID.eq(sensorId))
        .fetchOptional(mapper::toDomain)
        .orElseThrow(() -> new ObjectNotFoundException(SensorDocument.class));
  }
}
