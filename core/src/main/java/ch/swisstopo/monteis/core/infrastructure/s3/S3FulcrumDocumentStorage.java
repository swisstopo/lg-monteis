package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3FulcrumDocumentStorage implements FulcrumDocumentStorage {

  private final S3Client s3;
  private final DocumentStorageProperties properties;

  public S3FulcrumDocumentStorage(S3Client s3, DocumentStorageProperties properties) {
    this.s3 = s3;
    this.properties = properties;
  }

  @Override
  public void store(FulcrumDocumentFile file, InputStream content, Duration timeout) {
    long sizeBytes = file.metadata().sizeBytes();
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(file.attachmentId()))
            .contentType(file.metadata().contentType())
            .contentLength(sizeBytes)
            .overrideConfiguration(
                override -> override.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout))
            .build();
    s3.putObject(request, RequestBody.fromInputStream(content, sizeBytes));
  }

  @Override
  public InputStream load(UUID attachmentId) {
    return s3.getObject(
        GetObjectRequest.builder().bucket(properties.bucket()).key(keyOf(attachmentId)).build());
  }

  private String keyOf(UUID attachmentId) {
    return properties.fulcrumDocumentKeyPrefix() + attachmentId;
  }
}
