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
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryDetails;
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
          new DocumentMetadata("Jahns 03 2011 - DS Experiment.pdf", "application/pdf", 8));
  private static final FulcrumEntry ENTRY =
      new FulcrumEntry(
          new FulcrumEntryDetails(
              ENTRY_ID,
              "2011-02",
              "DS Experiment: BDS-1",
              "Jahns E.",
              2011,
              "2 Mont Terri Technical Notes (TN)",
              List.of("Experiment DS")),
          DOCUMENT,
          null);
  private static final FulcrumEntry REMOVED_ENTRY =
      new FulcrumEntry(
          new FulcrumEntryDetails(UUID.randomUUID(), null, "notes", null, null, null, List.of()),
          new FulcrumDocument(
              UUID.randomUUID(), new DocumentMetadata("notes.bin", "application/octet-stream", 3)),
          STARTED_AT);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FulcrumDocumentService documentService;
  @MockitoBean private FulcrumDocumentSyncService syncService;

  @Test
  void should_list_the_entries_with_their_document_to_anyone_signed_in() throws Exception {
    // given
    given(documentService.getEntries()).willReturn(List.of(ENTRY, REMOVED_ENTRY));

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
        .andExpect(jsonPath("$[0].document.id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$[0].document.fileName").value("Jahns 03 2011 - DS Experiment.pdf"))
        .andExpect(jsonPath("$[0].document.sizeBytes").value(8))
        .andExpect(jsonPath("$[0].document.viewable").value(true))
        .andExpect(jsonPath("$[1].availableInFulcrum").value(false))
        .andExpect(jsonPath("$[1].document.viewable").value(false));
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
  void should_show_the_sync_status_to_anyone_signed_in() throws Exception {
    // given
    given(syncService.getStatus()).willReturn(new FulcrumSyncStatus(false, STARTED_AT));

    // when / then
    mockMvc
        .perform(get(SYNC).with(authentication(PrivilegeLevel.BASISROLLE.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false))
        .andExpect(jsonPath("$.lastSyncedAt").exists());
  }

  @Test
  void should_start_a_sync_for_a_caller_with_document_access() throws Exception {
    // given
    given(syncService.startSync()).willReturn(new FulcrumSyncStatus(true, null));

    // when / then
    mockMvc
        .perform(
            post(SYNC)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.running").value(true));
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
    given(syncService.startSync()).willThrow(new FulcrumSyncAlreadyRunningException());

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
