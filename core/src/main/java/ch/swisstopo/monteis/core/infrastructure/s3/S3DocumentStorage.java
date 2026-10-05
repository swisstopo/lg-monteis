package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Keeps each document under {@code <keyPrefix><experimentId>/<documentId>}, so the key never
 * depends on the user supplied file name.
 */
@Component
public class S3DocumentStorage implements DocumentStorage {

  private final S3Client s3;
  private final DocumentStorageProperties properties;

  public S3DocumentStorage(S3Client s3, DocumentStorageProperties properties) {
    this.s3 = s3;
    this.properties = properties;
  }

  @Override
  public void store(ExperimentDocument document, InputStream content) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(document))
            .contentType(document.metadata().contentType())
            .contentLength(document.metadata().sizeBytes())
            .build();
    s3.putObject(request, RequestBody.fromInputStream(content, document.metadata().sizeBytes()));
  }

  @Override
  public InputStream load(ExperimentDocument document) {
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(properties.bucket()).key(keyOf(document)).build();
    return s3.getObject(request);
  }

  private String keyOf(ExperimentDocument document) {
    return properties.keyPrefix() + document.experimentId() + "/" + document.id();
  }
}
