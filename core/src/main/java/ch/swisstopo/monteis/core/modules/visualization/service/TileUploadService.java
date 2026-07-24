package ch.swisstopo.monteis.core.modules.visualization.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores a browser-generated 3D Tiles tileset (see the frontend's {@code tiling.worker.ts}) in
 * {@link TilingProperties#outputDir()}, replacing whatever tileset was there before, so {@code
 * TileController} can serve it.
 */
@Service
public class TileUploadService {
  private static final Logger log = LoggerFactory.getLogger(TileUploadService.class);

  private static final String TILESET_FILENAME = "tileset.json";
  private static final Pattern TILE_FILENAME = Pattern.compile("tile_\\d+\\.glb");

  private final TileConversionStatus status;
  private final TileOutputDirResolver outputDirResolver;

  public TileUploadService(TileConversionStatus status, TileOutputDirResolver outputDirResolver) {
    this.status = status;
    this.outputDirResolver = outputDirResolver;
  }

  public void store(List<MultipartFile> files) throws IOException {
    if (files.isEmpty()) {
      throw new ObjectBusinessValidationException("tiles.upload.empty", Map.of());
    }

    boolean hasTileset = false;
    for (MultipartFile file : files) {
      String filename = file.getOriginalFilename();
      if (filename == null || !isAllowedFilename(filename)) {
        throw new ObjectBusinessValidationException(
            "tiles.upload.rejectedFileName", Map.of("fileName", String.valueOf(filename)));
      }
      hasTileset |= filename.equals(TILESET_FILENAME);
    }
    if (!hasTileset) {
      throw new ObjectBusinessValidationException("tiles.upload.missingTileset", Map.of());
    }

    Path outputDir = outputDirResolver.resolve();
    Path stagingDir = outputDir.resolveSibling(outputDir.getFileName() + "-uploading");
    deleteRecursively(stagingDir);
    Files.createDirectories(stagingDir);

    for (MultipartFile file : files) {
      Path target = stagingDir.resolve(file.getOriginalFilename());
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }

    // Swapped in two steps rather than one atomic rename: outputDir may already contain a
    // previously served tileset, and a directory-to-directory move requires the destination to
    // not exist. The tileset is briefly unavailable to concurrent requests during the swap,
    // which is an acceptable tradeoff for a single-user POC.
    deleteRecursively(outputDir);
    Files.move(stagingDir, outputDir, StandardCopyOption.REPLACE_EXISTING);

    log.info("Stored {} uploaded tile files in {}", files.size(), outputDir);
    status.markReady(outputDir);
  }

  private static boolean isAllowedFilename(String filename) {
    return TILESET_FILENAME.equals(filename) || TILE_FILENAME.matcher(filename).matches();
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }
}
