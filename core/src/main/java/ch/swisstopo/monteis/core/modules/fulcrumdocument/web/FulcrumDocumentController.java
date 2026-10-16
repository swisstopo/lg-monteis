package ch.swisstopo.monteis.core.modules.fulcrumdocument.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumEntryResponseDto;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound.FulcrumSyncRunResponseDto;
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
  private final FulcrumDocumentSyncService syncService;
  private final FulcrumDocumentWebMapper mapper;
  private final CurrentUserProvider currentUserProvider;

  public FulcrumDocumentController(
      FulcrumDocumentService documentService,
      FulcrumDocumentSyncService syncService,
      FulcrumDocumentWebMapper mapper,
      CurrentUserProvider currentUserProvider) {
    this.documentService = documentService;
    this.syncService = syncService;
    this.mapper = mapper;
    this.currentUserProvider = currentUserProvider;
  }

  @Operation(
      summary = "Get the Fulcrum entries with their documents",
      description =
          "Retrieves all Fulcrum entries mirrored from Fulcrum with their stored documents,"
              + " unpaged and sorted by number (newest first), then title. Entries and documents"
              + " Fulcrum no longer has stay in the list with availableInFulcrum false.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved Fulcrum entries")
  @GetMapping(path = ApiPaths.FULCRUM_ENTRIES, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FulcrumEntryResponseDto>> getFulcrumEntries() {
    return ResponseEntity.ok(mapper.toDtos(documentService.getEntriesWithDocuments()));
  }

  @Operation(
      summary = "Download a Fulcrum Document",
      description =
          "Streams the content MONTEIS stored for the document as an attachment. Requires"
              + " documents:read.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed the document",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @GetMapping(path = ApiPaths.FULCRUM_DOCUMENT_CONTENT)
  public ResponseEntity<Resource> downloadFulcrumDocument(@PathVariable UUID documentId) {
    FulcrumDocument document = documentService.getDocument(documentId);
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
        .body(new InputStreamResource(documentService.openContent(document)));
  }

  @Operation(
      summary = "Get the latest Fulcrum sync",
      description = "The most recent sync run, scheduled or manual. 204 when none ever ran.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved the latest sync")
  @ApiResponse(responseCode = "204", description = "No sync ran yet")
  @GetMapping(path = ApiPaths.FULCRUM_SYNC, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FulcrumSyncRunResponseDto> getLatestFulcrumSyncRun() {
    return syncService
        .findLatestRun()
        .map(run -> ResponseEntity.ok(mapper.toRunDto(run)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(
      summary = "Start a Fulcrum sync",
      description =
          "Starts loading the Fulcrum entries and documents from Fulcrum again, in the"
              + " background. Poll the latest sync for its outcome. Requires documents:read.")
  @ApiResponse(responseCode = "202", description = "Sync started")
  @PostMapping(path = ApiPaths.FULCRUM_SYNC, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FulcrumSyncRunResponseDto> startFulcrumSync() {
    String triggeredBy = currentUserProvider.requireCurrentUsername();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(mapper.toRunDto(syncService.startSync(triggeredBy)));
  }
}
