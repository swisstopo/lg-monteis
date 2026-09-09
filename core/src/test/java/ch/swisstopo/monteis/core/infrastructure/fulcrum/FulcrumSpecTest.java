package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.contracts.fulcrum.BadRequestResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the parts of the Fulcrum contract that the Fulcrum client hardcodes and that no
 * generated model can cover (MON-142).
 *
 * <p>The spec is pinned to a commit of {@code fulcrumapp/api@v2} by {@code fulcrum.spec.ref} in
 * the root pom, downloaded during the contracts build and shipped in the contracts jar, so this
 * test reads exactly the document the models were generated from. Renovate raises a digest bump
 * for that pin and automerges it, which is only safe as long as something fails when Fulcrum
 * changes the endpoint, the authentication header or the parameters underneath us - that is this
 * test. A failure here means the Fulcrum client needs adjusting, not that the assertion is wrong.
 */
class FulcrumSpecTest {

  private static final String SPEC_RESOURCE = "/fulcrum/rest-api.json";

  /** Path the generated client uses; only referenced to locate the operation in the document. */
  private static final String QUERY_PATH = "/v2/query";

  private static final String SERVER_URL = "https://api.fulcrumapp.com/api";

  private static JsonNode spec;

  @BeforeAll
  static void loadSpec() throws IOException {
    try (InputStream in = FulcrumSpecTest.class.getResourceAsStream(SPEC_RESOURCE)) {
      assertThat(in)
          .as(
              "%s is downloaded into the contracts jar by the download-maven-plugin execution in"
                  + " contracts/pom.xml - run a build before this test",
              SPEC_RESOURCE)
          .isNotNull();
      spec = JsonMapper.builder().build().readTree(in);
    }
  }

  @Test
  void query_api_still_authenticates_with_the_x_api_token_header() {
    JsonNode scheme = spec.path("components").path("securitySchemes").path("ApiToken");

    assertThat(scheme.path("type").asString()).isEqualTo("apiKey");
    assertThat(scheme.path("in").asString()).isEqualTo("header");
    assertThat(scheme.path("name").asString()).isEqualTo("X-ApiToken");
  }

  @Test
  void configured_base_url_is_still_an_advertised_server() {
    List<String> servers =
        spec.path("servers").valueStream().map(s -> s.path("url").asString()).toList();

    assertThat(servers).contains(SERVER_URL);
  }

  @Test
  void rejected_queries_still_answer_with_the_generated_error_envelope() {
    JsonNode schema =
        spec.path("paths")
            .path(QUERY_PATH)
            .path("get")
            .path("responses")
            .path("400")
            .path("content")
            .path("application/json")
            .path("schema");

    assertThat(schema.path("$ref").asString())
        .isEqualTo("#/components/schemas/" + BadRequestResponse.class.getSimpleName());
    assertThat(
            spec.path("components")
                .path("schemas")
                .path("BadRequestResponse")
                .path("properties")
                .propertyNames())
        .containsExactlyInAnyOrder("error", "status");
  }

  @Test
  void query_results_are_still_untyped_by_the_spec() {
    JsonNode success =
        spec.path("paths")
            .path(QUERY_PATH)
            .path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("schema");

    assertThat(success.path("$ref").asString())
        .isEqualTo("#/components/schemas/EmptySuccessResponse");
    assertThat(
            spec.path("components")
                .path("schemas")
                .path("EmptySuccessResponse")
                .path("properties")
                .isEmpty())
        .as(
            "the row envelope of the Query API is undocumented, which is why the sensor row DTO is"
                + " hand-written - if Fulcrum starts typing it, generate the model instead")
        .isTrue();
  }
}
