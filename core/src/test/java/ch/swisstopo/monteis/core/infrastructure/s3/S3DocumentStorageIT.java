package ch.swisstopo.monteis.core.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.itconfig.S3TestcontainersConfiguration;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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

/** {@link S3DocumentStorage} against an S3Mock, through the client {@link S3Config} builds for it. */
@Testcontainers
class S3DocumentStorageIT {

  private static final String KEY_PREFIX = "experiment-documents/";

  @Container
  private static final GenericContainer<?> S3_MOCK = S3TestcontainersConfiguration.s3Mock();

  private static S3Client s3;
  private static S3DocumentStorage storage;

  @BeforeAll
  static void createStorage() {
    DocumentStorageProperties properties =
        new DocumentStorageProperties(
            S3TestcontainersConfiguration.BUCKET, "eu-central-1", KEY_PREFIX);
    s3 =
        new S3Config()
            .localS3Client(
                properties, URI.create(S3TestcontainersConfiguration.endpointOf(S3_MOCK)));
    storage = new S3DocumentStorage(s3, properties);
  }

  @AfterAll
  static void closeClient() {
    s3.close();
  }

  @Test
  void should_load_what_it_stored() throws IOException {
    // given
    byte[] content = "measured values".getBytes(StandardCharsets.UTF_8);
    ExperimentDocument document = document(content.length);

    // when
    storage.store(document, new ByteArrayInputStream(content));

    // then
    try (InputStream loaded = storage.load(document)) {
      assertThat(loaded.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void should_key_by_experiment_and_document_id_with_the_content_type() {
    // given
    byte[] content = "%PDF".getBytes(StandardCharsets.UTF_8);
    ExperimentDocument document = document(content.length);

    // when
    storage.store(document, new ByteArrayInputStream(content));

    // then
    HeadObjectResponse head =
        s3.headObject(
            request ->
                request
                    .bucket(S3TestcontainersConfiguration.BUCKET)
                    .key(KEY_PREFIX + document.experimentId() + "/" + document.id()));
    assertThat(head.contentType()).isEqualTo("application/pdf");
    assertThat(head.contentLength()).isEqualTo(content.length);
  }

  @Test
  void should_fail_to_load_a_document_that_was_never_stored() {
    assertThrows(NoSuchKeyException.class, () -> storage.load(document(1)));
  }

  private static ExperimentDocument document(long sizeBytes) {
    return new ExperimentDocument(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "report.pdf",
        "application/pdf",
        sizeBytes,
        OffsetDateTime.now(),
        "alice");
  }
}
