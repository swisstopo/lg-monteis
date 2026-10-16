package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_DOCUMENTS;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentRepository;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentDocumentRepository implements ExperimentDocumentRepository {

  private final DSLContext dsl;
  private final ExperimentDocumentJooqMapper mapper;

  public JooqExperimentDocumentRepository(DSLContext dsl, ExperimentDocumentJooqMapper mapper) {
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
  public ExperimentDocument create(
      UUID id, UUID experimentId, DocumentMetadata metadata, String uploadedBy) {
    return dsl.insertInto(EXPERIMENT_DOCUMENTS)
        .set(EXPERIMENT_DOCUMENTS.ID, id)
        .set(EXPERIMENT_DOCUMENTS.EXPERIMENT_ID, experimentId)
        .set(EXPERIMENT_DOCUMENTS.FILE_NAME, metadata.fileName())
        .set(EXPERIMENT_DOCUMENTS.CONTENT_TYPE, metadata.contentType())
        .set(EXPERIMENT_DOCUMENTS.SIZE_BYTES, metadata.sizeBytes())
        .set(EXPERIMENT_DOCUMENTS.UPLOADED_BY, uploadedBy)
        .returning()
        .fetchSingle(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ExperimentDocument> findByExperimentId(UUID experimentId) {
    return dsl.selectFrom(EXPERIMENT_DOCUMENTS)
        .where(EXPERIMENT_DOCUMENTS.EXPERIMENT_ID.eq(experimentId))
        .orderBy(EXPERIMENT_DOCUMENTS.UPLOADED_AT.desc(), EXPERIMENT_DOCUMENTS.ID.desc())
        .fetch(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public ExperimentDocument getById(UUID experimentId, UUID documentId) {
    return dsl.selectFrom(EXPERIMENT_DOCUMENTS)
        .where(EXPERIMENT_DOCUMENTS.ID.eq(documentId))
        .and(EXPERIMENT_DOCUMENTS.EXPERIMENT_ID.eq(experimentId))
        .fetchOptional(mapper::toDomain)
        .orElseThrow(() -> new ObjectNotFoundException(ExperimentDocument.class));
  }
}
