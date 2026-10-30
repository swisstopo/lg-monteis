package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class S3ExperimentDocumentStorage extends S3DocumentStorage
    implements ExperimentDocumentStorage {

  public S3ExperimentDocumentStorage(S3Client s3, DocumentStorageProperties properties) {
    super(s3, properties.bucket(), properties.experimentDocumentKeyPrefix());
  }
}
