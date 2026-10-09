package ch.swisstopo.monteis.core.modules.experiment.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentDocumentService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@ControllerTest(ExperimentDocumentController.class)
@Import(ExperimentDocumentWebMapperImpl.class)
class ExperimentDocumentControllerTest {

  private static final String DOCUMENTS = "/api/experiments/{id}/documents";
  private static final String DOCUMENT_CONTENT = DOCUMENTS + "/{documentId}/content";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final ExperimentDocument DOCUMENT =
      new ExperimentDocument(
          UUID.randomUUID(),
          ASSIGNED_EXPERIMENT,
          new DocumentMetadata("Bericht Mai.pdf", "application/pdf", CONTENT.length),
          OffsetDateTime.of(2026, 2, 13, 9, 3, 26, 0, ZoneOffset.UTC),
          "experiment_pi");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ExperimentDocumentService service;

  @Test
  void should_list_the_documents_of_an_experiment() throws Exception {
    // given
    given(service.getDocuments(ASSIGNED_EXPERIMENT)).willReturn(List.of(DOCUMENT));

    // when / then
    mockMvc
        .perform(get(DOCUMENTS, ASSIGNED_EXPERIMENT).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$[0].experimentId").value(ASSIGNED_EXPERIMENT.toString()))
        .andExpect(jsonPath("$[0].fileName").value("Bericht Mai.pdf"))
        .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
        .andExpect(jsonPath("$[0].sizeBytes").value(CONTENT.length))
        .andExpect(jsonPath("$[0].viewable").value(true))
        .andExpect(jsonPath("$[0].uploadedBy").value("experiment_pi"))
        .andExpect(jsonPath("$[0].uploadedAt").exists());
  }

  @Test
  void should_answer_404_for_the_documents_of_a_hidden_experiment() throws Exception {
    // given
    given(service.getDocuments(OTHER_EXPERIMENT))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then
    mockMvc
        .perform(get(DOCUMENTS, OTHER_EXPERIMENT).with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }

  @Test
  void should_store_an_upload_and_answer_201() throws Exception {
    // given
    given(service.upload(eq(ASSIGNED_EXPERIMENT), any(), any(InputStream.class)))
        .willReturn(DOCUMENT);

    // when / then
    mockMvc
        .perform(
            uploadRequest(ASSIGNED_EXPERIMENT, file("C:\\Users\\pi\\Bericht Mai.pdf", CONTENT))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$.fileName").value("Bericht Mai.pdf"));

    then(service)
        .should()
        .upload(
            eq(ASSIGNED_EXPERIMENT),
            eq(new DocumentMetadata("Bericht Mai.pdf", "application/pdf", CONTENT.length)),
            any(InputStream.class));
  }

  @Test
  void should_reject_an_empty_upload() throws Exception {
    // when / then
    mockMvc
        .perform(
            uploadRequest(ASSIGNED_EXPERIMENT, file("empty.pdf", new byte[0]))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.messageKey").value("document.validation.empty"));

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_an_upload_to_an_experiment_the_caller_may_only_read() throws Exception {
    // when / then
    mockMvc
        .perform(
            uploadRequest(OTHER_EXPERIMENT, file("report.pdf", CONTENT))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.messageKey").value("access.denied"));

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_stream_a_document_as_attachment() throws Exception {
    // given
    given(service.getDocument(ASSIGNED_EXPERIMENT, DOCUMENT.id())).willReturn(DOCUMENT);
    given(service.openContent(DOCUMENT)).willReturn(new ByteArrayInputStream(CONTENT));

    // when / then
    mockMvc
        .perform(get(DOCUMENT_CONTENT, ASSIGNED_EXPERIMENT, DOCUMENT.id()).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().longValue("Content-Length", CONTENT.length))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", containsString("filename*=UTF-8''Bericht%20Mai.pdf")))
        .andExpect(content().bytes(CONTENT));
  }

  @Test
  void should_answer_404_for_an_unknown_document() throws Exception {
    // given
    UUID documentId = UUID.randomUUID();
    given(service.getDocument(ASSIGNED_EXPERIMENT, documentId))
        .willThrow(new ObjectNotFoundException(ExperimentDocument.class));

    // when / then
    mockMvc
        .perform(get(DOCUMENT_CONTENT, ASSIGNED_EXPERIMENT, documentId).with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));

    then(service).should(never()).openContent(any());
  }

  private static MockMultipartFile file(String originalFileName, byte[] content) {
    return new MockMultipartFile("file", originalFileName, "application/pdf", content);
  }

  private static MockMultipartHttpServletRequestBuilder uploadRequest(
      UUID experimentId, MockMultipartFile file) {
    return multipart(DOCUMENTS, experimentId).file(file).with(csrf());
  }
}
