package ch.swisstopo.monteis.core.infrastructure.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the "Authorize" bearer-token scheme used by Swagger UI and stamps every documented
 * operation with it, so the spec reflects that the API is not public. The actual per-method
 * authority distinction (reads vs. {@code api:write} for writes) is enforced in {@code
 * SecurityConfig}, not modeled here — OpenAPI can't express real scopes on a plain HTTP bearer
 * scheme, and the frontend sends the same JWT to every request regardless.
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_AUTH = "bearerAuth";

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme()));
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

  private static SecurityScheme bearerScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("Bearer token for any authenticated user.");
  }

  private static void requireScheme(Operation operation) {
    if (operation != null) {
      operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
  }
}
