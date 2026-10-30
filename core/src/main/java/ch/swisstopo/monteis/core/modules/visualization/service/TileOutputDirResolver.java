package ch.swisstopo.monteis.core.modules.visualization.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link TilingProperties#outputDir()} to an actual filesystem path. A relative
 * {@code output-dir} is deliberately NOT resolved against the JVM's working directory: that
 * varies with how the app happens to be launched ({@code mvn spring-boot:run} from {@code core/}
 * vs. an IDE run config with the repo root as its working directory, etc.), and previously caused
 * uploaded tiles to land in a phantom {@code <wherever>/src/main/resources/tiles} instead of the
 * actual {@code core} module. Anchoring on the classpath root instead is cwd-independent by
 * construction: {@code classpath:/} always resolves to the real classes directory (normally
 * {@code core/target/classes}, mirrored 1:1 from {@code core/src/main/resources} by Maven), which
 * this then maps back to the source tree so generated tiles are visible in the repo rather than
 * wiped by the next {@code mvn clean}.
 */
@Component
class TileOutputDirResolver {

  private final TilingProperties properties;
  private final ResourceLoader resourceLoader;

  TileOutputDirResolver(TilingProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  Path resolve() {
    Path configured = Path.of(properties.outputDir());
    if (configured.isAbsolute()) {
      return configured;
    }

    return resourcesRoot().resolve(configured);
  }

  private Path resourcesRoot() {
    Resource classpathRoot = resourceLoader.getResource("classpath:/");
    Path classesDir;
    try {
      classesDir = classpathRoot.getFile().toPath();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not resolve the classpath root to a filesystem path - relative"
              + " monteis.tiling.output-dir requires running from an exploded classpath (e.g. mvn"
              + " spring-boot:run), not a packaged jar. Configure an absolute output-dir instead.",
          e);
    }

    // Maps any Maven build output dir (target/classes when run normally, target/test-classes
    // under Surefire, ...) back to the module's actual source resources, so this resolves the
    // same real directory regardless of which classpath happened to answer "classpath:/" first.
    for (int i = 0; i < classesDir.getNameCount(); i++) {
      if (classesDir.getName(i).toString().equals("target")) {
        Path root = classesDir.getRoot();
        Path relativeModuleDir = classesDir.subpath(0, i);
        Path moduleDir = root == null ? relativeModuleDir : root.resolve(relativeModuleDir);
        return moduleDir.resolve("src/main/resources");
      }
    }
    return classesDir;
  }
}
