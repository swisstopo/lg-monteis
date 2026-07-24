package ch.swisstopo.monteis.core.modules.visualization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TileOutputDirResolverTest {

  @Test
  void resolves_a_relative_output_dir_against_the_source_resources_directory_not_the_cwd() {
    // Maven always runs tests with the module directory as the working directory, so this is a
    // safe assumption for the TEST's own expectation - it is NOT an assumption the resolver
    // itself is allowed to make (that was the actual bug: see class Javadoc).
    Path expected = Path.of("src/main/resources/tiles").toAbsolutePath();

    TilingProperties properties = new TilingProperties("tiles");
    TileOutputDirResolver resolver =
        new TileOutputDirResolver(properties, new DefaultResourceLoader());

    assertEquals(expected, resolver.resolve());
  }

  @Test
  void an_absolute_output_dir_is_used_as_is() {
    Path absolute = Path.of("/tmp/somewhere/tiles").toAbsolutePath();
    TilingProperties properties = new TilingProperties(absolute.toString());
    TileOutputDirResolver resolver =
        new TileOutputDirResolver(properties, new DefaultResourceLoader());

    assertEquals(absolute, resolver.resolve());
  }
}
