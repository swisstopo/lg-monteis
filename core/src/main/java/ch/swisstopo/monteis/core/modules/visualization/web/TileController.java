package ch.swisstopo.monteis.core.modules.visualization.web;

import ch.swisstopo.monteis.core.modules.visualization.service.TileConversionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the tileset uploaded via {@code TileUploadController} (or found already present on
 * startup - see {@code TileStartupStatusChecker}) straight off disk. Answers 503 until a tileset
 * exists, so the frontend can poll rather than treat a missing tileset as a permanent 404.
 */
@RestController
public class TileController {

  private final TileConversionStatus status;

  public TileController(TileConversionStatus status) {
    this.status = status;
  }

  @GetMapping("/model/tiles/{*path}")
  public ResponseEntity<Resource> getTile(@PathVariable String path) throws IOException {
    if (!status.isReady()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    Path outputDir = status.outputDir().toAbsolutePath().normalize();
    String relativePath = path.startsWith("/") ? path.substring(1) : path;
    Path file = outputDir.resolve(relativePath).normalize();

    if (!file.startsWith(outputDir) || !Files.isRegularFile(file)) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok().contentType(mediaTypeFor(file)).body(new FileSystemResource(file));
  }

  private MediaType mediaTypeFor(Path file) {
    String name = file.getFileName().toString();
    if (name.endsWith(".json")) {
      return MediaType.APPLICATION_JSON;
    }
    if (name.endsWith(".glb")) {
      return MediaType.parseMediaType("model/gltf-binary");
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
