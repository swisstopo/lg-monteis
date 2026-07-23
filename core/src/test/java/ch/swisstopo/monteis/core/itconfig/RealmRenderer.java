package ch.swisstopo.monteis.core.itconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Merges the Keycloak realm base config with the local patch, mirroring the jq transform in
 * docker/keycloak/realm/render.sh, so the e2e Testcontainer imports the exact same realm, clients
 * and users as the docker-compose Keycloak used for local dev. Keep the two in sync manually if
 * that patch's shape ever changes.
 */
final class RealmRenderer {

  static final String CLASSPATH_RESOURCE_NAME = "realm-e2e.json";

  private RealmRenderer() {}

  static void render(String monteisRoot) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      File realmDir = new File(monteisRoot, "docker/keycloak/realm");
      ObjectNode base = (ObjectNode) mapper.readTree(new File(realmDir, "realm-base.json"));
      JsonNode patch = mapper.readTree(new File(realmDir, "patch.local.json"));

      JsonNode spaPatch = patch.path("spa");
      for (JsonNode client : base.withArray("clients")) {
        if ("monteis-spa".equals(client.path("clientId").asText())) {
          ((ObjectNode) client).set("redirectUris", spaPatch.path("redirectUris"));
          ((ObjectNode) client).set("webOrigins", spaPatch.path("webOrigins"));
        }
      }

      ArrayNode users = base.withArray("users");
      patch.withArray("users").forEach(users::add);

      File rendered = new File("target/test-classes/" + CLASSPATH_RESOURCE_NAME);
      rendered.getParentFile().mkdirs();
      mapper.writeValue(rendered, base);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to render the Keycloak e2e realm", e);
    }
  }
}
