package ch.swisstopo.monteis.core.modules.sensor.web;

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

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorDocumentService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@ControllerTest(SensorDocumentController.class)
@Import(SensorDocumentWebMapperImpl.class)
class SensorDocumentControllerTest {

  private static final String DOCUMENTS = "/api/sensors/{id}/documents";
  private static final String DOCUMENT_CONTENT = DOCUMENTS + "/{documentId}/content";
  private static final UUID SENSOR_ID = UUID.randomUUID();
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final SensorDocument DOCUMENT =
      new SensorDocument(
          UUID.randomUUID(),
          SENSOR_ID,
          new DocumentMetadata("Kalibrierung.pdf", "application/pdf", CONTENT.length),
          OffsetDateTime.of(2026, 10, 28, 9, 3, 26, 0, ZoneOffset.UTC),
          "experiment_pi");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SensorDocumentService service;

  @Test
  void should_list_the_documents_of_a_sensor() throws Exception {
    // given
    given(service.getDocuments(SENSOR_ID)).willReturn(List.of(DOCUMENT));

    // when / then
    mockMvc
        .perform(get(DOCUMENTS, SENSOR_ID).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$[0].fileName").value("Kalibrierung.pdf"))
        .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
        .andExpect(jsonPath("$[0].sizeBytes").value(CONTENT.length))
        .andExpect(jsonPath("$[0].viewable").value(true))
        .andExpect(jsonPath("$[0].uploadedBy").value("experiment_pi"))
        .andExpect(jsonPath("$[0].uploadedAt").exists());
  }

  @Test
  void should_answer_404_for_the_documents_of_a_hidden_sensor() throws Exception {
    // given
    given(service.getDocuments(SENSOR_ID)).willThrow(new ObjectNotFoundException(Sensor.class));

    // when / then
    mockMvc
        .perform(get(DOCUMENTS, SENSOR_ID).with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }

  @Test
  void should_store_an_upload_and_answer_201() throws Exception {
    // given
    given(service.upload(eq(SENSOR_ID), any(), any(InputStream.class))).willReturn(DOCUMENT);

    // when / then
    mockMvc
        .perform(
            uploadRequest(file("C:\\Users\\pi\\Kalibrierung.pdf", CONTENT))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(DOCUMENT.id().toString()))
        .andExpect(jsonPath("$.fileName").value("Kalibrierung.pdf"));

    then(service)
        .should()
        .upload(
            eq(SENSOR_ID),
            eq(new DocumentMetadata("Kalibrierung.pdf", "application/pdf", CONTENT.length)),
            any(InputStream.class));
  }

  @Test
  void should_reject_an_empty_upload() throws Exception {
    // when / then
    mockMvc
        .perform(
            uploadRequest(file("empty.pdf", new byte[0]))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.messageKey").value("document.validation.empty"));

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_403_when_the_service_refuses_the_upload() throws Exception {
    // given
    given(service.upload(eq(SENSOR_ID), any(), any(InputStream.class)))
        .willThrow(new AccessDeniedException("no write access"));

    // when / then
    mockMvc
        .perform(
            uploadRequest(file("report.pdf", CONTENT))
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.messageKey").value("access.denied"));
  }

  @Test
  void should_stream_a_document_as_attachment() throws Exception {
    // given
    given(service.getDocument(SENSOR_ID, DOCUMENT.id())).willReturn(DOCUMENT);
    given(service.openContent(DOCUMENT)).willReturn(new ByteArrayInputStream(CONTENT));

    // when / then
    mockMvc
        .perform(get(DOCUMENT_CONTENT, SENSOR_ID, DOCUMENT.id()).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().longValue("Content-Length", CONTENT.length))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(
            header()
                .string("Content-Disposition", containsString("filename*=UTF-8''Kalibrierung.pdf")))
        .andExpect(content().bytes(CONTENT));
  }

  @Test
  void should_answer_404_for_an_unknown_document() throws Exception {
    // given
    UUID documentId = UUID.randomUUID();
    given(service.getDocument(SENSOR_ID, documentId))
        .willThrow(new ObjectNotFoundException(SensorDocument.class));

    // when / then
    mockMvc
        .perform(get(DOCUMENT_CONTENT, SENSOR_ID, documentId).with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));

    then(service).should(never()).openContent(any());
  }

  private static MockMultipartFile file(String originalFileName, byte[] content) {
    return new MockMultipartFile("file", originalFileName, "application/pdf", content);
  }

  private static MockMultipartHttpServletRequestBuilder uploadRequest(MockMultipartFile file) {
    return multipart(DOCUMENTS, SENSOR_ID).file(file).with(csrf());
  }
}
