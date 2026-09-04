package ch.swisstopo.monteis.core.infrastructure.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The 3D tilesets under {@code src/main/resources/static/api/tilesets} are served by Spring Boot's
 * default static resource handling — there is no controller and no {@code addResourceHandlers}
 * registration, so the {@code api/} directory in that path <em>is</em> the URL prefix. Nothing in
 * the code base would break visibly if that convention stopped holding, hence this test.
 */
@ControllerTest
@ContextConfiguration(classes = TilesetResourceTest.NoControllers.class)
class TilesetResourceTest {

  private static final String TILESET = "/api/tilesets/example/tileset.json";
  private static final String TILE = "/api/tilesets/example/ifc/13.b3dm";

  @Autowired private MockMvc mockMvc;

  @Test
  void should_serve_the_tileset_under_the_api_prefix() throws Exception {
    mockMvc
        .perform(get(TILESET).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.root.children").isArray());
  }

  @Test
  void should_serve_the_individual_tiles() throws Exception {
    mockMvc
        .perform(get(TILE).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/octet-stream"));
  }

  @Test
  void should_require_authentication_for_the_tileset() throws Exception {
    mockMvc.perform(get(TILESET)).andExpect(status().isUnauthorized());
  }

  @Test
  void should_require_authentication_for_the_individual_tiles() throws Exception {
    mockMvc.perform(get(TILE)).andExpect(status().isUnauthorized());
  }

  /** This slice needs the static resource handling only, not any application controller. */
  @Configuration
  static class NoControllers {}
}
