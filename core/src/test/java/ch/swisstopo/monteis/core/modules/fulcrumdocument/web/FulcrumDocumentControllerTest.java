package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryWithDocuments;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(FulcrumDocumentController.class)
@Import(FulcrumDocumentWebMapperImpl.class)
class FulcrumDocumentControllerTest {

  private static final String ENTRIES = "/api/fulcrum-entries";
  private static final String DOCUMENT_CONTENT = "/api/fulcrum-documents/{documentId}/content";
  private static final String SYNC = "/api/fulcrum-sync";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final OffsetDateTime STARTED_AT =
      OffsetDateTime.of(2026, 10, 15, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID ENTRY_ID = UUID.randomUUID();
  private static final FulcrumDocument DOCUMENT =
      new FulcrumDocument(
          UUID.randomUUID(),
          ENTRY_ID,
          new DocumentMetadata("Jahns 03 2011 - DS Experiment.pdf", "application/pdf", 8),
          STARTED_AT,
          null);
  private static final FulcrumDocument REMOVED_DOCUMENT =
      new FulcrumDocument(
          UUID.randomUUID(),
          ENTRY_ID,
          new DocumentMetadata("notes.bin", "application/octet-stream", 3),
          STARTED_AT,
          STARTED_AT);
  private static final FulcrumEntry ENTRY =
      new FulcrumEntry(
          ENTRY_ID,
          "2011-02",
          "DS Experiment: BDS-1",
          "Jahns E.",
          2011,
          "2 Mont Terri Technical Notes (TN)",
          List.of("Experiment DS"),
          List.of(
              new FulcrumAttachment(DOCUMENT.id(), ENTRY_ID, "Jahns 03 2011 - DS Experiment.pdf")),
          null);
  private static final FulcrumSyncRun RUN =
      new FulcrumSyncRun(
          UUID.randomUUID(),
          FulcrumSyncStatus.RUNNING,
          "experiment_user",
          STARTED_AT,
          null,
          null,
          null,
          null);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FulcrumDocumentService documentService;
  @MockitoBean private FulcrumDocumentSyncService syncService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @Test
  void should_list_the_entries_with_their_documents_to_anyone_signed_in() throws Exception {
    // given
    given(documentService.getEntriesWithDocuments())
        .willReturn(
            List.of(new FulcrumEntryWithDocuments(ENTRY, List.of(DOCUMENT, REMOVED_DOCUMENT))));

    // when / then
    mockMvc
        .perform(get(ENTRIES).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ENTRY_ID.toString()))
        .andExpect(jsonPath("$[0].number").value("2011-02"))
        .andExpect(jsonPath("$[0].title").value("DS Experiment: BDS-1"))
        .andExpect(jsonPath("$[0].authors").value("Jahns E."))
        .andExpect(jsonPath("$[0].year").value(2011))
        .andExpect(jsonPath("$[0].category").value("2 Mont Terri Technical Notes (TN)"))
        .andExpect(jsonPath("$[0].keywords[0]").value("Experiment DS"))
        .andExpect(jsonPath("$[0].availableInFulcrum").value(true))
        .andExpect(jsonPath("$[0].documents[0].id").value(DOCUMENT.id().toString()))
        .andExpect(
            jsonPath("$[0].documents[0].fileName").value("Jahns 03 2011 - DS Experiment.pdf"))
        .andExpect(jsonPath("$[0].documents[0].sizeBytes").value(8))
        .andExpect(jsonPath("$[0].documents[0].viewable").value(true))
        .andExpect(jsonPath("$[0].documents[0].availableInFulcrum").value(true))
        .andExpect(jsonPath("$[0].documents[1].viewable").value(false))
        .andExpect(jsonPath("$[0].documents[1].availableInFulcrum").value(false));
  }

  @Test
  void should_stream_a_document_to_a_caller_with_document_access() throws Exception {
    // given
    given(documentService.getDocument(DOCUMENT.id())).willReturn(DOCUMENT);
    given(documentService.openContent(DOCUMENT)).willReturn(new ByteArrayInputStream(CONTENT));

    // when / then
    mockMvc
        .perform(
            get(DOCUMENT_CONTENT, DOCUMENT.id())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(content().bytes(CONTENT));
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
  void should_show_the_latest_sync_to_anyone_signed_in() throws Exception {
    // given
    given(syncService.findLatestRun()).willReturn(Optional.of(RUN));

    // when / then
    mockMvc
        .perform(get(SYNC).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RUNNING"))
        .andExpect(jsonPath("$.triggeredBy").value("experiment_user"))
        .andExpect(jsonPath("$.startedAt").exists());
  }

  @Test
  void should_answer_204_when_no_sync_ran_yet() throws Exception {
    // given
    given(syncService.findLatestRun()).willReturn(Optional.empty());

    // when / then
    mockMvc
        .perform(get(SYNC).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isNoContent());
  }

  @Test
  void should_start_a_sync_for_a_caller_with_document_access() throws Exception {
    // given
    given(currentUserProvider.requireCurrentUsername()).willReturn("experiment_user");
    given(syncService.startSync("experiment_user")).willReturn(RUN);

    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(RUN.id().toString()))
        .andExpect(jsonPath("$.status").value("RUNNING"));
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
  void should_answer_409_while_a_sync_is_already_running() throws Exception {
    // given
    given(currentUserProvider.requireCurrentUsername()).willReturn("experiment_user");
    given(syncService.startSync("experiment_user"))
        .willThrow(new FulcrumSyncAlreadyRunningException());

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
