package ch.swisstopo.monteis.core.modules.visualization.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * On startup, marks the tileset READY if one is already present in the output directory - e.g.
 * from an upload before the last restart - so the frontend doesn't need to re-upload every time.
 *
 * <p>Tiling itself happens entirely client-side: the frontend parses and tiles the IFC file
 * in-browser (see {@code tiling.worker.ts}) and uploads the result to {@code
 * TileUploadController}. The backend never touches an IFC file or a tiling binary - it only
 * stores whatever tileset it's handed and serves it back via {@code TileController}.
 */
@Service
public class TileStartupStatusChecker {
  private static final Logger log = LoggerFactory.getLogger(TileStartupStatusChecker.class);

  private final TileConversionStatus status;
  private final TileOutputDirResolver outputDirResolver;

  public TileStartupStatusChecker(
      TileConversionStatus status, TileOutputDirResolver outputDirResolver) {
    this.status = status;
    this.outputDirResolver = outputDirResolver;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void checkForExistingTileset() {
    Path outputDir = outputDirResolver.resolve();
    if (Files.isRegularFile(outputDir.resolve("tileset.json"))) {
      log.info("Found an existing tileset in {}, serving it.", outputDir);
      status.markReady(outputDir);
    } else {
      log.info("No tileset found in {} yet - waiting for an upload.", outputDir);
    }
  }
}
