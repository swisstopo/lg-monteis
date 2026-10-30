package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_SYNC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.FulcrumStub;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.StubDocument;
import ch.swisstopo.monteis.core.itconfig.FulcrumStubConfiguration.StubFile;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.S3TestcontainersConfiguration;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// not @Transactional: sync saves in own tx per document
@IT
@Import(FulcrumStubConfiguration.class)
@TestPropertySource(
    properties = {"monteis.fulcrum.enabled=true", "monteis.fulcrum.documents.max-file-size=1KB"})
class FulcrumDocumentSyncServiceIT {

  private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final byte[] OTHER_PDF = "%PDF-2.0".getBytes(StandardCharsets.UTF_8);
  private static final byte[] LARGER_THAN_MAX_FILE_SIZE = new byte[2048];

  @Container
  private static final GenericContainer<?> S3_MOCK = S3TestcontainersConfiguration.s3Mock();

  @DynamicPropertySource
  static void storeInTheS3Mock(DynamicPropertyRegistry registry) {
    registry.add("monteis.documents.s3.bucket", () -> S3TestcontainersConfiguration.BUCKET);
    registry.add(
        "monteis.documents.s3.local-endpoint",
        () -> S3TestcontainersConfiguration.endpointOf(S3_MOCK));
  }

  @Autowired private FulcrumDocumentSyncService syncService;
  @Autowired private FulcrumDocumentRepository documentRepository;
  @Autowired private FulcrumSyncRepository syncRepository;
  @Autowired private FulcrumDocumentStorage storage;
  @Autowired private FulcrumStub fulcrum;
  @Autowired private DSLContext dsl;

  @AfterEach
  void removeTheSyncedDocuments() {
    fulcrum.serveE2eDocuments();
    dsl.deleteFrom(FULCRUM_DOCUMENTS).execute();
    dsl.deleteFrom(FULCRUM_DOCUMENT_SYNC).execute();
  }

  @Test
  void should_import_the_documents_with_their_files_and_count_the_sync_as_successful() {
    // given
    StubFile file = StubFile.of("report.pdf", PDF);
    StubDocument document = StubDocument.of("2026-01", file);
    fulcrum.serveDocuments(document);

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedDocumentOf(document).hasFile(file.attachmentId())).isTrue();
    assertThat(storedContentOf(file)).isEqualTo(PDF);
    assertThat(syncRepository.findLastSuccessfulSyncAt()).isPresent();
  }

  @Test
  void should_replace_the_file_of_a_document_once_fulcrum_lists_a_new_one() {
    // given
    StubDocument document = StubDocument.of("2026-01", StubFile.of("report.pdf", PDF));
    fulcrum.serveDocuments(document);
    syncWithTimeLeft();
    StubFile newFile = StubFile.of("report-v2.pdf", OTHER_PDF);
    fulcrum.serveDocuments(document.withFile(newFile));

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedDocumentOf(document).hasFile(newFile.attachmentId())).isTrue();
    assertThat(storedContentOf(newFile)).isEqualTo(OTHER_PDF);
  }

  @Test
  void should_keep_the_old_file_while_the_new_one_cannot_be_downloaded() {
    // given
    StubFile oldFile = StubFile.of("report.pdf", PDF);
    StubDocument document = StubDocument.of("2026-01", oldFile);
    fulcrum.serveDocuments(document);
    syncWithTimeLeft();
    fulcrum.serveDocuments(document.withFile(StubFile.notDownloadable("report-v2.pdf")));

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedDocumentOf(document).hasFile(oldFile.attachmentId())).isTrue();
    assertThat(storedContentOf(oldFile)).isEqualTo(PDF);
  }

  @Test
  void should_mark_a_document_removed_once_fulcrum_no_longer_lists_it() {
    // given
    StubDocument removed = StubDocument.of("2026-01", StubFile.of("old.pdf", PDF));
    StubDocument kept = StubDocument.of("2026-02", StubFile.of("kept.pdf", PDF));
    fulcrum.serveDocuments(removed, kept);
    syncWithTimeLeft();
    fulcrum.serveDocuments(kept);

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedDocumentOf(removed).isAvailableInFulcrum()).isFalse();
    assertThat(storedDocumentOf(kept).isAvailableInFulcrum()).isTrue();
  }

  @Test
  void should_skip_a_file_larger_than_allowed_and_still_count_the_sync_as_successful() {
    // given
    StubDocument tooLarge =
        StubDocument.of("2026-01", StubFile.of("huge.pdf", LARGER_THAN_MAX_FILE_SIZE));
    StubFile file = StubFile.of("report.pdf", PDF);
    StubDocument imported = StubDocument.of("2026-02", file);
    fulcrum.serveDocuments(tooLarge, imported);

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedIds()).containsExactly(imported.recordId());
    assertThat(storedContentOf(file)).isEqualTo(PDF);
    assertThat(syncRepository.findLastSuccessfulSyncAt()).isPresent();
  }

  @Test
  void should_import_the_other_files_but_not_count_the_sync_when_a_download_fails() {
    // given
    StubDocument failing = StubDocument.of("2026-01", StubFile.notDownloadable("gone.pdf"));
    StubDocument imported = StubDocument.of("2026-02", StubFile.of("report.pdf", PDF));
    fulcrum.serveDocuments(failing, imported);

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedIds()).containsExactly(imported.recordId());
    assertThat(syncRepository.findLastSuccessfulSyncAt()).isEmpty();
  }

  @Test
  void should_neither_remove_documents_nor_count_the_sync_when_fulcrum_returns_nothing() {
    // given
    StubDocument document = StubDocument.of("2026-01", StubFile.of("report.pdf", PDF));
    fulcrum.serveDocuments(document);
    syncWithTimeLeft();
    dsl.deleteFrom(FULCRUM_DOCUMENT_SYNC).execute();
    fulcrum.serveDocuments();

    // when
    syncWithTimeLeft();

    // then
    assertThat(storedDocumentOf(document).isAvailableInFulcrum()).isTrue();
    assertThat(syncRepository.findLastSuccessfulSyncAt()).isEmpty();
  }

  @Test
  void should_import_nothing_and_not_count_the_sync_once_its_time_is_up() {
    // given
    StubFile file = StubFile.of("report.pdf", PDF);
    fulcrum.serveDocuments(StubDocument.of("2026-01", file));

    // when
    SystemSecurityContext.runAsSystem(() -> syncService.sync(Instant.now().minusSeconds(1)));

    // then
    assertThat(storedIds()).isEmpty();
    assertThatThrownBy(() -> storedContentOf(file)).isInstanceOf(NoSuchKeyException.class);
    assertThat(syncRepository.findLastSuccessfulSyncAt()).isEmpty();
  }

  private void syncWithTimeLeft() {
    SystemSecurityContext.runAsSystem(
        () -> syncService.sync(Instant.now().plus(Duration.ofMinutes(1))));
  }

  private FulcrumDocument storedDocumentOf(StubDocument document) {
    return documentRepository.getById(document.recordId());
  }

  private List<UUID> storedIds() {
    return documentRepository.findAll().stream().map(FulcrumDocument::id).toList();
  }

  private byte[] storedContentOf(StubFile file) {
    try (InputStream content = storage.load(file.attachmentId())) {
      return content.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
