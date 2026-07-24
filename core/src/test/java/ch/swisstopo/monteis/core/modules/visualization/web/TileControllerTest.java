package ch.swisstopo.monteis.core.modules.visualization.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.visualization.service.TileConversionStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(TileController.class)
class TileControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TileConversionStatus status;

  private Path outputDir;

  @AfterEach
  void cleanUp() throws Exception {
    if (outputDir != null) {
      Files.deleteIfExists(outputDir.resolve("tileset.json"));
      Files.deleteIfExists(outputDir);
    }
  }

  @Test
  void should_answer_503_while_conversion_is_not_ready() throws Exception {
    given(status.isReady()).willReturn(false);

    mockMvc
        .perform(get("/model/tiles/tileset.json").with(jwt()))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void should_serve_tile_file_once_ready() throws Exception {
    outputDir = Files.createTempDirectory("tile-controller-test");
    Files.writeString(outputDir.resolve("tileset.json"), "{\"asset\":{\"version\":\"1.1\"}}");

    given(status.isReady()).willReturn(true);
    given(status.outputDir()).willReturn(outputDir);

    mockMvc
        .perform(get("/model/tiles/tileset.json").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                org.junit.jupiter.api.Assertions.assertEquals(
                    MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType()));
  }

  @Test
  void should_reject_paths_escaping_the_output_directory() throws Exception {
    outputDir = Files.createTempDirectory("tile-controller-test");

    given(status.isReady()).willReturn(true);
    given(status.outputDir()).willReturn(outputDir);

    // The servlet container itself normalizes and rejects "../" traversal in the URL before this
    // ever reaches the controller; TileController's own normalize()/startsWith() check is
    // defense in depth for whatever the container doesn't already catch.
    mockMvc
        .perform(get("/model/tiles/../../etc/passwd").with(jwt()))
        .andExpect(status().isBadRequest());
  }
}
