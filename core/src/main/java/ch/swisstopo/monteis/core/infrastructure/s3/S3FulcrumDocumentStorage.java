package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// keyed by Fulcrum's attachment id under its own prefix, so the files never mix with the
// experiment documents and can always be fetched from Fulcrum again
@Component
public class S3FulcrumDocumentStorage implements FulcrumDocumentStorage {

  private final S3Client s3;
  private final DocumentStorageProperties properties;

  public S3FulcrumDocumentStorage(S3Client s3, DocumentStorageProperties properties) {
    this.s3 = s3;
    this.properties = properties;
  }

  @Override
  public void store(UUID fileId, String contentType, Path content) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(keyOf(fileId))
            .contentType(contentType)
            .build();
    s3.putObject(request, RequestBody.fromFile(content));
  }

  @Override
  public InputStream load(UUID fileId) {
    return s3.getObject(
        GetObjectRequest.builder().bucket(properties.bucket()).key(keyOf(fileId)).build());
  }

  private String keyOf(UUID fileId) {
    return properties.fulcrumKeyPrefix() + fileId;
  }
}
