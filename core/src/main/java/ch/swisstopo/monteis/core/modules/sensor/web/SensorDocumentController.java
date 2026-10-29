package ch.swisstopo.monteis.core.modules.sensor.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.api.DocumentResponseDto;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorDocumentService;
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
@RequestMapping(ApiPaths.SENSOR_DOCUMENTS)
public class SensorDocumentController {

  private final SensorDocumentService service;
  private final SensorDocumentWebMapper mapper;

  public SensorDocumentController(SensorDocumentService service, SensorDocumentWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get the documents of a sensor",
      description = "Retrieves the metadata of all documents of a sensor, newest first.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved documents")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<DocumentResponseDto>> getSensorDocuments(@PathVariable UUID id) {
    return ResponseEntity.ok(mapper.toDtos(service.getDocuments(id)));
  }

  @Operation(
      summary = "Upload a document to a sensor",
      description =
          "Stores the file and records the upload time and the uploader. A file with the same"
              + " name as an existing document is stored as an additional document.")
  @ApiResponse(responseCode = "201", description = "Document successfully uploaded")
  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<DocumentResponseDto> uploadSensorDocument(
      @PathVariable UUID id, @RequestPart("file") MultipartFile file) throws IOException {
    DocumentMetadata metadata = mapper.toMetadata(file);
    try (InputStream content = file.getInputStream()) {
      SensorDocument document = service.upload(id, metadata, content);
      return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(document));
    }
  }

  @Operation(
      summary = "Download a document of a sensor",
      description = "Streams the content of the document as an attachment.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed the document",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @GetMapping(path = "{documentId}/content")
  public ResponseEntity<Resource> downloadSensorDocument(
      @PathVariable UUID id, @PathVariable UUID documentId) {
    SensorDocument document = service.getDocument(id, documentId);
    DocumentMetadata metadata = document.metadata();
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(metadata.fileName(), StandardCharsets.UTF_8)
            .build();
    MediaType contentType = MediaType.parseMediaType(metadata.contentType());
    // opened last: if building the headers throws, no S3 connection is left open
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(contentType)
        .contentLength(metadata.sizeBytes())
        .body(new InputStreamResource(service.openContent(document)));
  }
}
