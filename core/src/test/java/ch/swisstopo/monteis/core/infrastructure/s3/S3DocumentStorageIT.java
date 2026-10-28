package ch.swisstopo.monteis.core.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.itconfig.S3TestcontainersConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * {@link S3DocumentStorage} against an S3Mock, through the client {@link S3Config} builds. That way
 * the endpoint switch and path-style access are covered too, not only the storage.
 */
@Testcontainers
class S3DocumentStorageIT {

  private static final String EXPERIMENT_KEY_PREFIX = "experiment-documents/";
  private static final String SENSOR_KEY_PREFIX = "sensor-documents/";
  private static final DocumentMetadata PDF =
      new DocumentMetadata("report.pdf", "application/pdf", 4);

  @Container
  private static final GenericContainer<?> S3_MOCK = S3TestcontainersConfiguration.s3Mock();

  private static S3Client s3;
  private static S3ExperimentDocumentStorage experimentStorage;
  private static S3SensorDocumentStorage sensorStorage;

  @BeforeAll
  static void createStorage() {
    DocumentStorageProperties properties =
        new DocumentStorageProperties(
            S3TestcontainersConfiguration.BUCKET,
            "eu-central-1",
            EXPERIMENT_KEY_PREFIX,
            SENSOR_KEY_PREFIX,
            "fulcrum-documents/",
            Duration.ofSeconds(60),
            Duration.ofSeconds(30),
            URI.create(S3TestcontainersConfiguration.endpointOf(S3_MOCK)));
    s3 = new S3Config().s3Client(properties);
    experimentStorage = new S3ExperimentDocumentStorage(s3, properties);
    sensorStorage = new S3SensorDocumentStorage(s3, properties);
  }

  @AfterAll
  static void closeClient() {
    s3.close();
  }

  @Test
  void should_load_what_it_stored() throws IOException {
    // given
    byte[] content = "measured values".getBytes(StandardCharsets.UTF_8);
    DocumentKey key = randomKey();

    // when
    experimentStorage.store(
        key,
        new DocumentMetadata("values.txt", "text/plain", content.length),
        new ByteArrayInputStream(content));

    // then
    try (InputStream loaded = experimentStorage.load(key)) {
      assertThat(loaded.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void should_key_experiment_documents_by_experiment_and_document_id_with_the_content_type() {
    // given
    DocumentKey key = randomKey();

    // when
    experimentStorage.store(key, PDF, pdfContent());

    // then
    HeadObjectResponse head = headOf(EXPERIMENT_KEY_PREFIX, key);
    assertThat(head.contentType()).isEqualTo("application/pdf");
    assertThat(head.contentLength()).isEqualTo(PDF.sizeBytes());
  }

  @Test
  void should_key_sensor_documents_by_sensor_and_document_id() {
    // given
    DocumentKey key = randomKey();

    // when
    sensorStorage.store(key, PDF, pdfContent());

    // then
    assertThat(headOf(SENSOR_KEY_PREFIX, key).contentLength()).isEqualTo(PDF.sizeBytes());
    assertThrows(NoSuchKeyException.class, () -> experimentStorage.load(key));
  }

  @Test
  void should_not_load_a_deleted_document() {
    // given
    DocumentKey key = randomKey();
    sensorStorage.store(key, PDF, pdfContent());

    // when
    sensorStorage.delete(key);

    // then
    assertThrows(NoSuchKeyException.class, () -> sensorStorage.load(key));
  }

  @Test
  void should_fail_to_load_a_document_that_was_never_stored() {
    // when / then
    assertThrows(NoSuchKeyException.class, () -> experimentStorage.load(randomKey()));
  }

  private static HeadObjectResponse headOf(String keyPrefix, DocumentKey key) {
    return s3.headObject(
        request ->
            request
                .bucket(S3TestcontainersConfiguration.BUCKET)
                .key(keyPrefix + key.ownerId() + "/" + key.documentId()));
  }

  private static InputStream pdfContent() {
    return new ByteArrayInputStream("%PDF".getBytes(StandardCharsets.UTF_8));
  }

  private static DocumentKey randomKey() {
    return new DocumentKey(UUID.randomUUID(), UUID.randomUUID());
  }
}
