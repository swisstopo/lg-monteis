package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Keeps each document under {@code <keyPrefix><experimentId>/<documentId>}. The file name is not
 * part of the key, two uploads with the same name would overwrite each other and the user would
 * decide what the key looks like.
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
  public void store(
      UUID experimentId, UUID documentId, DocumentMetadata metadata, InputStream content) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(experimentId, documentId))
            .contentType(metadata.contentType())
            .contentLength(metadata.sizeBytes())
            .build();
    s3.putObject(request, RequestBody.fromInputStream(content, metadata.sizeBytes()));
  }

  @Override
  public InputStream load(ExperimentDocument document) {
    GetObjectRequest request =
        GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(document.experimentId(), document.id()))
            .build();
    return s3.getObject(request);
  }

  @Override
  public void delete(UUID experimentId, UUID documentId) {
    DeleteObjectRequest request =
        DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(experimentId, documentId))
            .build();
    s3.deleteObject(request);
  }

  private String keyOf(UUID experimentId, UUID documentId) {
    return properties.keyPrefix() + experimentId + "/" + documentId;
  }
}
