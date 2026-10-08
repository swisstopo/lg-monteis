package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentDocumentService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentDocumentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiPaths.EXPERIMENT_DOCUMENTS)
public class ExperimentDocumentController {

  private final ExperimentDocumentService service;
  private final ExperimentDocumentWebMapper mapper;

  public ExperimentDocumentController(
      ExperimentDocumentService service, ExperimentDocumentWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get the documents of an experiment",
      description = "Retrieves the metadata of all documents of an experiment, newest first.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ExperimentDocumentResponseDto>> getDocuments(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID experimentId) {
    return ResponseEntity.ok(mapper.toDtos(service.getDocuments(experimentId)));
  }

  @Operation(
      summary = "Upload a document to an experiment",
      description =
          "Stores the file and records the upload time and the uploader. A file with the same"
              + " name as an existing document is stored as an additional document.")
  @ApiResponse(responseCode = "201", description = "Document successfully uploaded")
  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentDocumentResponseDto> uploadDocument(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID experimentId,
      @RequestPart("file") MultipartFile file)
      throws IOException {
    DocumentMetadata metadata = mapper.toMetadata(file);
    try (InputStream content = file.getInputStream()) {
      ExperimentDocument document = service.upload(experimentId, metadata, content);
      return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(document));
    }
  }

  @Operation(
      summary = "Download a document of an experiment",
      description = "Streams the content of the document as an attachment.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed the document",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @GetMapping(path = "{documentId}/content")
  public ResponseEntity<Resource> downloadDocument(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID experimentId, @PathVariable UUID documentId) {
    ExperimentDocument document = service.getDocument(experimentId, documentId);
    DocumentMetadata metadata = document.metadata();
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(metadata.fileName(), StandardCharsets.UTF_8)
            .build();
    MediaType contentType = MediaType.parseMediaType(metadata.contentType());
    // opened last: if building the headers throws, no S3 connection is left open
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(contentType)
        .contentLength(metadata.sizeBytes())
        .body(new InputStreamResource(service.openContent(document)));
  }
}
