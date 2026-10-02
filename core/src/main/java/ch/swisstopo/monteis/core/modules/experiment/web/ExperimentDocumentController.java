package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentUpload;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.service.DocumentDownload;
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
import java.util.Map;
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
@RequestMapping("/api/experiments/{id}/documents")
public class ExperimentDocumentController {

  // column length of experiment_documents.file_name and .content_type
  private static final int MAX_LENGTH = 255;

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
      @PathVariable("id") UUID experimentId) {
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
      @PathVariable("id") UUID experimentId, @RequestPart("file") MultipartFile file)
      throws IOException {
    if (file.isEmpty()) {
      throw new ObjectBusinessValidationException("document.validation.empty", Map.of());
    }
    DocumentUpload upload =
        new DocumentUpload(
            fileNameOf(file.getOriginalFilename()), contentTypeOf(file), file.getSize());
    try (InputStream content = file.getInputStream()) {
      ExperimentDocument document = service.upload(experimentId, upload, content);
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
      @PathVariable("id") UUID experimentId, @PathVariable UUID documentId) {
    DocumentDownload download = service.download(experimentId, documentId);
    ExperimentDocument document = download.document();
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(document.fileName(), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType(document.contentType()))
        .contentLength(document.sizeBytes())
        .body(new InputStreamResource(download.content()));
  }

  private static String fileNameOf(String originalFilename) {
    String fileName = lastPathSegment(originalFilename == null ? "" : originalFilename).strip();
    if (fileName.isEmpty() || fileName.length() > MAX_LENGTH) {
      throw new ObjectBusinessValidationException(
          "document.validation.fileName", Map.of("max", MAX_LENGTH));
    }
    return fileName;
  }

  // some browsers send the full client path
  private static String lastPathSegment(String path) {
    return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
  }

  private static String contentTypeOf(MultipartFile file) {
    try {
      String contentType = MediaType.parseMediaType(file.getContentType()).toString();
      return contentType.length() <= MAX_LENGTH
          ? contentType
          : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    } catch (RuntimeException _) {
      return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
  }
}
