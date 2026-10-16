package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ControllerTest(FulcrumDocumentController.class)
@Import(FulcrumDocumentWebMapperImpl.class)
class FulcrumDocumentControllerTest {

  private static final String DOCUMENTS = "/api/fulcrum-documents";
  private static final String DOCUMENT_CONTENT = "/api/fulcrum-documents/{documentId}/content";
  private static final String SYNC = "/api/fulcrum-sync";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final OffsetDateTime STARTED_AT =
      OffsetDateTime.of(2026, 10, 15, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final FulcrumDocument DOCUMENT =
      new FulcrumDocument(
          UUID.randomUUID(),
          "2011-02",
          "DS Experiment: BDS-1",
          "Jahns E.",
          2011,
          "2 Mont Terri Technical Notes (TN)",
          List.of("Experiment DS"),
          new FulcrumDocumentFile(
              UUID.randomUUID(),
              new DocumentMetadata("Jahns 03 2011 - DS Experiment.pdf", "application/pdf", 8)),
          null);
  private static final FulcrumDocument REMOVED_DOCUMENT =
      new FulcrumDocument(
          UUID.randomUUID(),
          null,
          "notes",
          null,
          null,
          null,
          List.of(),
          new FulcrumDocumentFile(
              UUID.randomUUID(), new DocumentMetadata("notes.bin", "application/octet-stream", 3)),
          STARTED_AT);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FulcrumDocumentService documentService;
  @MockitoBean private FulcrumDocumentSyncService syncService;

  @Test
  void should_list_the_documents_with_their_file_to_anyone_signed_in() throws Exception {
    // given
    given(documentService.getDocuments()).willReturn(List.of(DOCUMENT, REMOVED_DOCUMENT));

    // when / then
    mockMvc
        .perform(get(DOCUMENTS).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$[0].number").value("2011-02"))
        .andExpect(jsonPath("$[0].title").value("DS Experiment: BDS-1"))
        .andExpect(jsonPath("$[0].authors").value("Jahns E."))
        .andExpect(jsonPath("$[0].year").value(2011))
        .andExpect(jsonPath("$[0].category").value("2 Mont Terri Technical Notes (TN)"))
        .andExpect(jsonPath("$[0].keywords[0]").value("Experiment DS"))
        .andExpect(jsonPath("$[0].availableInFulcrum").value(true))
        .andExpect(jsonPath("$[0].file.fileName").value("Jahns 03 2011 - DS Experiment.pdf"))
        .andExpect(jsonPath("$[0].file.sizeBytes").value(8))
        .andExpect(jsonPath("$[0].file.viewable").value(true))
        .andExpect(jsonPath("$[0].file.attachmentId").doesNotExist())
        .andExpect(jsonPath("$[1].availableInFulcrum").value(false))
        .andExpect(jsonPath("$[1].file.viewable").value(false));
  }

  @Test
  void should_stream_a_document_to_a_caller_with_document_access() throws Exception {
    // given
    given(documentService.getDocument(DOCUMENT.id())).willReturn(DOCUMENT);
    given(documentService.openFileContent(DOCUMENT)).willReturn(new ByteArrayInputStream(CONTENT));

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, DOCUMENT.id())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(header().longValue("Content-Length", 8))
        .andExpect(content().bytes(CONTENT));
  }

  @Test
  void should_encode_a_file_name_outside_ascii() throws Exception {
    // given
    FulcrumDocument withUmlaut =
        new FulcrumDocument(
            UUID.randomUUID(),
            null,
            "Gräben",
            null,
            null,
            null,
            List.of(),
            new FulcrumDocumentFile(
                UUID.randomUUID(),
                new DocumentMetadata("Bericht Gräben.pdf", "application/pdf", 8)),
            null);
    given(documentService.getDocument(withUmlaut.id())).willReturn(withUmlaut);
    given(documentService.openFileContent(withUmlaut))
        .willReturn(new ByteArrayInputStream(CONTENT));

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, withUmlaut.id())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    containsString("filename*=UTF-8''Bericht%20Gr%C3%A4ben.pdf")));
  }

  @Test
  void should_stream_the_backup_of_a_document_fulcrum_no_longer_has() throws Exception {
    // given
    given(documentService.getDocument(REMOVED_DOCUMENT.id())).willReturn(REMOVED_DOCUMENT);
    given(documentService.openFileContent(REMOVED_DOCUMENT))
        .willReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, REMOVED_DOCUMENT.id())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isOk())
        .andExpect(content().bytes(new byte[] {1, 2, 3}));
  }

  // only a bug or a hand deleted object gets here, S3 is written before the row
  @Test
  void should_answer_500_when_the_file_is_missing_in_storage() throws Exception {
    // given
    given(documentService.getDocument(DOCUMENT.id())).willReturn(DOCUMENT);
    given(documentService.openFileContent(DOCUMENT))
        .willThrow(
            NoSuchKeyException.builder().message("The specified key does not exist").build());

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, DOCUMENT.id())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_forbid_a_document_to_a_caller_without_document_access() throws Exception {
    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, DOCUMENT.id())
                .with(authentication(PrivilegeLevel.GLOBAL_EDITOR.authentication())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.messageKey").value("access.denied"));

    then(documentService).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_head_request_for_a_document_without_document_access() throws Exception {
    // when / then
    mockMvc
        .perform(
            head(DOCUMENT_CONTENT, DOCUMENT.id())
                .with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isForbidden());

    then(documentService).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_for_an_unknown_document() throws Exception {
    // given
    UUID unknown = UUID.randomUUID();
    given(documentService.getDocument(unknown))
        .willThrow(new ObjectNotFoundException(FulcrumDocument.class));

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, unknown)
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }

  @Test
  void should_show_the_sync_status_to_anyone_signed_in() throws Exception {
    // given
    given(syncService.currentStatus()).willReturn(new FulcrumSyncStatus(false, STARTED_AT));

    // when / then
    mockMvc
        .perform(get(SYNC).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false))
        .andExpect(jsonPath("$.lastSuccessfulSyncAt").value("2026-10-15T02:00:00Z"));
  }

  @Test
  void should_start_a_sync_for_a_caller_with_document_access_and_report_it_running()
      throws Exception {
    // given
    given(syncService.currentStatus()).willReturn(new FulcrumSyncStatus(true, null));

    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.running").value(true));

    then(syncService).should().startSyncInBackground();
  }

  @Test
  void should_forbid_a_sync_to_a_caller_without_document_access() throws Exception {
    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isForbidden());

    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_503_while_fulcrum_is_switched_off() throws Exception {
    // given
    willThrow(new FulcrumDisabledException()).given(syncService).startSyncInBackground();

    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.messageKey").value("fulcrum.disabled"));
  }

  @Test
  void should_answer_409_while_a_sync_is_already_running() throws Exception {
    // given
    willThrow(new FulcrumSyncAlreadyRunningException()).given(syncService).startSyncInBackground();

    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.messageKey").value("fulcrum.sync.alreadyRunning"));
  }
}
