package ch.swisstopo.monteis.core.infrastructure.s3;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.io.InputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Keeps each document under {@code <keyPrefix><parentId>/<documentId>}, one subclass per parent
 * sets the prefix. The file name is not part of the key, two uploads with the same name would
 * overwrite each other and the user would decide what the key looks like.
 */
public abstract class S3DocumentStorage {

  private final S3Client s3;
  private final String bucket;
  private final String keyPrefix;

  protected S3DocumentStorage(S3Client s3, String bucket, String keyPrefix) {
    this.s3 = s3;
    this.bucket = bucket;
    this.keyPrefix = keyPrefix;
  }

  public void store(DocumentKey key, DocumentMetadata metadata, InputStream content) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKeyOf(key))
            .contentType(metadata.contentType())
            .contentLength(metadata.sizeBytes())
            .build();
    s3.putObject(request, RequestBody.fromInputStream(content, metadata.sizeBytes()));
  }

  public InputStream load(DocumentKey key) {
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(bucket).key(objectKeyOf(key)).build();
    return s3.getObject(request);
  }

  public void delete(DocumentKey key) {
    DeleteObjectRequest request =
        DeleteObjectRequest.builder().bucket(bucket).key(objectKeyOf(key)).build();
    s3.deleteObject(request);
  }

  private String objectKeyOf(DocumentKey key) {
    return keyPrefix + key.parentId() + "/" + key.documentId();
  }
}
