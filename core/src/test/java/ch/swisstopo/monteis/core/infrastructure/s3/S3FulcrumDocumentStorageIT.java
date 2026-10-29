package ch.swisstopo.monteis.core.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.itconfig.S3TestcontainersConfiguration;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
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

@Testcontainers
class S3FulcrumDocumentStorageIT {

  private static final String FULCRUM_KEY_PREFIX = "fulcrum-documents/";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

  @Container
  private static final GenericContainer<?> S3_MOCK = S3TestcontainersConfiguration.s3Mock();

  private static S3Client s3;
  private static S3FulcrumDocumentStorage storage;

  @BeforeAll
  static void createStorage() {
    DocumentStorageProperties properties =
        new DocumentStorageProperties(
            S3TestcontainersConfiguration.BUCKET,
            "eu-central-1",
            "experiment-documents/",
            FULCRUM_KEY_PREFIX,
            Duration.ofSeconds(60),
            Duration.ofSeconds(30),
            URI.create(S3TestcontainersConfiguration.endpointOf(S3_MOCK)));
    s3 = new S3Config().s3Client(properties);
    storage = new S3FulcrumDocumentStorage(s3, properties);
  }

  @AfterAll
  static void closeClient() {
    s3.close();
  }

  @Test
  void should_load_what_it_stored() throws IOException {
    // given
    UUID documentId = UUID.randomUUID();

    // when
    storage.store(pdf(documentId), new ByteArrayInputStream(CONTENT), Duration.ofMinutes(1));

    // then
    try (InputStream loaded = storage.load(documentId)) {
      assertThat(loaded.readAllBytes()).isEqualTo(CONTENT);
    }
  }

  @Test
  void should_key_by_document_id_apart_from_the_experiment_documents() {
    // given
    UUID documentId = UUID.randomUUID();

    // when
    storage.store(pdf(documentId), new ByteArrayInputStream(CONTENT), Duration.ofMinutes(1));

    // then
    HeadObjectResponse head =
        s3.headObject(
            request ->
                request
                    .bucket(S3TestcontainersConfiguration.BUCKET)
                    .key(FULCRUM_KEY_PREFIX + documentId));
    assertThat(head.contentType()).isEqualTo("application/pdf");
    assertThat(head.contentLength()).isEqualTo(CONTENT.length);
  }

  @Test
  void should_fail_to_load_a_document_that_was_never_stored() {
    // given
    UUID unknownId = UUID.randomUUID();

    // when / then
    assertThrows(NoSuchKeyException.class, () -> storage.load(unknownId));
  }

  private static FulcrumDocumentFile pdf(UUID documentId) {
    return new FulcrumDocumentFile(
        documentId, new DocumentMetadata("report.pdf", "application/pdf", CONTENT.length));
  }
}
