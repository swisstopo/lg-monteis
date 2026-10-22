package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncRunner;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumDocumentResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumSyncStatusResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FulcrumDocumentController {

  private final FulcrumDocumentService documentService;
  private final FulcrumDocumentSyncRunner syncRunner;
  private final FulcrumDocumentWebMapper mapper;

  public FulcrumDocumentController(
      FulcrumDocumentService documentService,
      FulcrumDocumentSyncRunner syncRunner,
      FulcrumDocumentWebMapper mapper) {
    this.documentService = documentService;
    this.syncRunner = syncRunner;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get the Fulcrum Documents",
      description =
          "Retrieves all Fulcrum Documents mirrored from Fulcrum with the metadata of their file,"
              + " unpaged and sorted by number (newest first), then title. Documents Fulcrum no"
              + " longer has stay in the list with availableInFulcrum false.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved Fulcrum Documents")
  @GetMapping(path = ApiPaths.FULCRUM_DOCUMENTS, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FulcrumDocumentResponseDto>> getFulcrumDocuments() {
    return ResponseEntity.ok(mapper.toDtos(documentService.getDocuments()));
  }

  @Operation(
      summary = "Download the file of a Fulcrum Document",
      description =
          "Streams the file MONTEIS stored for the document as an attachment. Requires"
              + " documents:read.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed the file",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @GetMapping(path = ApiPaths.FULCRUM_DOCUMENT_CONTENT)
  public ResponseEntity<Resource> downloadFulcrumDocumentFile(@PathVariable UUID documentId) {
    FulcrumDocument document = documentService.getDocument(documentId);
    DocumentMetadata metadata = document.file().metadata();
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(metadata.fileName(), StandardCharsets.UTF_8)
            .build();
    MediaType contentType = MediaType.parseMediaType(metadata.contentType());
    // opened last: header failure leaves no S3 connection open
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(contentType)
        .contentLength(metadata.sizeBytes())
        .body(new InputStreamResource(documentService.openFileContent(document)));
  }

  @Operation(
      summary = "Get the Fulcrum sync status",
      description =
          "Whether a sync is running right now, and when the last successful sync started: it"
              + " reached Fulcrum, saved every document and imported every file within its time"
              + " budget (null before the first one).")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved the sync status")
  @GetMapping(path = ApiPaths.FULCRUM_SYNC, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FulcrumSyncStatusResponseDto> getFulcrumSyncStatus() {
    return ResponseEntity.ok(mapper.toStatusDto(syncRunner.currentStatus()));
  }

  @Operation(
      summary = "Start a Fulcrum sync",
      description =
          "Starts loading the Fulcrum Documents and their files from Fulcrum again, in the"
              + " background. Poll the sync status until it no longer runs. Requires"
              + " documents:read.")
  @ApiResponse(responseCode = "202", description = "Sync started")
  @PostMapping(path = ApiPaths.FULCRUM_SYNC, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FulcrumSyncStatusResponseDto> startFulcrumSync() {
    syncRunner.startInBackground();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(mapper.toStatusDto(syncRunner.currentStatus()));
  }
}
