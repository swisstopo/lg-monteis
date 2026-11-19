package ch.swisstopo.monteis.core.infrastructure.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Registers the "Authorize" schemes used by Swagger UI and stamps every documented operation with
 * them, so the spec reflects that the API is not public. The actual per-method authority
 * distinction (reads vs. {@code api:admin} for writes) is enforced in {@code SecurityConfig}, not
 * modeled here — OpenAPI can't express real scopes on these schemes, and the frontend sends the
 * same JWT to every request regardless.
 *
 * <p>Two alternatives are offered (OpenAPI security items are OR-ed): paste a bearer token, or run
 * the Keycloak authorization-code flow from Swagger UI itself ({@code oauth2}). The flow URLs are
 * derived from the resource server's issuer; the scheme is omitted when no real issuer is
 * configured (the {@code openapi} profile used for the TS code generation sets a stub).
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_AUTH = "bearerAuth";
  private static final String OAUTH_2 = "oauth2";
  private static final String STUB_ISSUER = "stub";

  private final String issuerUri;

  public OpenApiConfig(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
    this.issuerUri = issuerUri;
  }

  @Bean
  public OpenAPI openApi() {
    Components components = new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme());
    if (hasKeycloakIssuer()) {
      components.addSecuritySchemes(OAUTH_2, oauth2Scheme());
    }
    return new OpenAPI().components(components);
  }

  @Bean
  public OpenApiCustomizer authorizationRequirementsCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem -> {
                  requireScheme(pathItem.getGet());
                  requireScheme(pathItem.getHead());
                  requireScheme(pathItem.getOptions());
                  requireScheme(pathItem.getPost());
                  requireScheme(pathItem.getPut());
                  requireScheme(pathItem.getPatch());
                  requireScheme(pathItem.getDelete());
                });
  }

  private SecurityScheme oauth2Scheme() {
    String base = StringUtils.trimTrailingCharacter(issuerUri, '/') + "/protocol/openid-connect";
    OAuthFlow authorizationCode =
        new OAuthFlow()
            .authorizationUrl(base + "/auth")
            .tokenUrl(base + "/token")
            .refreshUrl(base + "/token")
            .scopes(new Scopes().addString("openid", "OpenID Connect"));
    return new SecurityScheme()
        .type(SecurityScheme.Type.OAUTH2)
        .description("Log in at Keycloak and let Swagger UI fetch the token (PKCE).")
        .flows(new OAuthFlows().authorizationCode(authorizationCode));
  }

  private boolean hasKeycloakIssuer() {
    return StringUtils.hasText(issuerUri) && !STUB_ISSUER.equals(issuerUri);
  }

  private static SecurityScheme bearerScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("Bearer token for any authenticated user.");
  }

  private void requireScheme(Operation operation) {
    if (operation == null) {
      return;
    }
    operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    if (hasKeycloakIssuer()) {
      operation.addSecurityItem(new SecurityRequirement().addList(OAUTH_2));
    }
  }
}
