package ch.swisstopo.monteis.core.modules.visualization.web;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.WRITE_AUTHORITY;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.visualization.service.TileUploadService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(TileUploadController.class)
class TileUploadControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TileUploadService service;

  @Test
  void should_store_uploaded_tiles_and_return_201() throws Exception {
    MockMultipartFile tileset =
        new MockMultipartFile("files", "tileset.json", "application/json", "{}".getBytes());
    MockMultipartFile tile =
        new MockMultipartFile("files", "tile_0.glb", "model/gltf-binary", new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/api/tiles")
                .file(tileset)
                .file(tile)
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY))))
        .andExpect(status().isCreated());

    then(service).should().store(java.util.List.of(tileset, tile));
  }

  @Test
  void should_translate_validation_failure_into_422() throws Exception {
    MockMultipartFile tileset =
        new MockMultipartFile("files", "tileset.json", "application/json", "{}".getBytes());

    willThrow(new ObjectBusinessValidationException("tiles.upload.missingTileset", Map.of()))
        .given(service)
        .store(java.util.List.of(tileset));

    mockMvc
        .perform(
            multipart("/api/tiles")
                .file(tileset)
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY))))
        .andExpect(status().isUnprocessableEntity());
  }
}
