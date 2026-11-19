package ch.swisstopo.monteis.core.infrastructure.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The OAuth2 scheme of {@link OpenApiConfig} only appears with a real issuer; the bearer scheme is
 * always there.
 */
class OpenApiConfigTest {

  private static final String ISSUER = "http://localhost:8081/auth/realms/monteis";

  @Test
  void should_register_bearer_and_keycloak_oauth2_schemes_for_a_real_issuer() {
    // when
    Map<String, SecurityScheme> schemes =
        new OpenApiConfig(ISSUER).openApi().getComponents().getSecuritySchemes();

    // then
    assertEquals(Set.of("bearerAuth", "oauth2"), schemes.keySet());
    assertEquals(SecurityScheme.Type.HTTP, schemes.get("bearerAuth").getType());
    assertEquals("bearer", schemes.get("bearerAuth").getScheme());

    SecurityScheme oauth2 = schemes.get("oauth2");
    assertEquals(SecurityScheme.Type.OAUTH2, oauth2.getType());
    OAuthFlow flow = oauth2.getFlows().getAuthorizationCode();
    assertEquals(ISSUER + "/protocol/openid-connect/auth", flow.getAuthorizationUrl());
    assertEquals(ISSUER + "/protocol/openid-connect/token", flow.getTokenUrl());
    assertEquals(ISSUER + "/protocol/openid-connect/token", flow.getRefreshUrl());
    assertEquals(Set.of("openid"), flow.getScopes().keySet());
    assertNull(oauth2.getFlows().getImplicit());
    assertNull(oauth2.getFlows().getPassword());
    assertNull(oauth2.getFlows().getClientCredentials());
  }

  @Test
  void should_strip_trailing_slashes_from_the_issuer() {
    // when
    SecurityScheme oauth2 =
        new OpenApiConfig(ISSUER + "//")
            .openApi()
            .getComponents()
            .getSecuritySchemes()
            .get("oauth2");

    // then
    assertEquals(
        ISSUER + "/protocol/openid-connect/auth",
        oauth2.getFlows().getAuthorizationCode().getAuthorizationUrl());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "stub"})
  void should_register_only_the_bearer_scheme_without_a_real_issuer(String issuer) {
    // when
    Map<String, SecurityScheme> schemes =
        new OpenApiConfig(issuer).openApi().getComponents().getSecuritySchemes();

    // then
    assertEquals(Set.of("bearerAuth"), schemes.keySet());
  }

  @Test
  void should_offer_bearer_or_oauth2_on_every_operation_for_a_real_issuer() {
    // given
    PathItem pathItem = pathItemWithAllMethods();
    OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/test", pathItem));

    // when
    new OpenApiConfig(ISSUER).authorizationRequirementsCustomizer().customise(openApi);

    // then
    List<SecurityRequirement> expected =
        List.of(
            new SecurityRequirement().addList("bearerAuth"),
            new SecurityRequirement().addList("oauth2"));
    pathItem.readOperations().forEach(operation -> assertEquals(expected, operation.getSecurity()));
    assertEquals(7, pathItem.readOperations().size());
  }

  @Test
  void should_require_only_bearer_on_every_operation_without_a_real_issuer() {
    // given
    PathItem pathItem = pathItemWithAllMethods();
    OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/test", pathItem));

    // when
    new OpenApiConfig("stub").authorizationRequirementsCustomizer().customise(openApi);

    // then
    pathItem
        .readOperations()
        .forEach(
            operation ->
                assertEquals(
                    List.of(new SecurityRequirement().addList("bearerAuth")),
                    operation.getSecurity()));
  }

  private static PathItem pathItemWithAllMethods() {
    return new PathItem()
        .get(new Operation())
        .head(new Operation())
        .options(new Operation())
        .post(new Operation())
        .put(new Operation())
        .patch(new Operation())
        .delete(new Operation());
  }
}
