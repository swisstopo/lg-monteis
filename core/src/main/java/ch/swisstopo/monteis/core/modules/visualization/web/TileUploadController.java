package ch.swisstopo.monteis.core.modules.visualization.web;

import ch.swisstopo.monteis.core.modules.visualization.service.TileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Receives a 3D Tiles tileset generated client-side (see the frontend's {@code tiling.worker.ts},
 * which tiles an uploaded IFC file entirely in a Web Worker) and stores it for {@code
 * TileController} to serve under {@code /model/tiles/**}.
 */
@RestController
@RequestMapping("/api/tiles")
public class TileUploadController {

  private final TileUploadService service;

  public TileUploadController(TileUploadService service) {
    this.service = service;
  }

  @Operation(
      summary = "Upload a browser-tiled 3D Tiles tileset",
      description =
          "Accepts tileset.json plus its tile_N.glb files, produced client-side from an IFC"
              + " upload, and replaces the tileset served at /model/tiles/**.")
  @ApiResponse(responseCode = "201", description = "Tileset stored and now being served")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> upload(@RequestParam("files") List<MultipartFile> files)
      throws IOException {
    service.store(files);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
