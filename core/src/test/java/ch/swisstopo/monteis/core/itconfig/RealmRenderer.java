package ch.swisstopo.monteis.core.itconfig;

import java.io.File;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
        if ("monteis-spa".equals(client.path("clientId").asString())) {
          ((ObjectNode) client).set("redirectUris", spaPatch.path("redirectUris"));
          ((ObjectNode) client).set("webOrigins", spaPatch.path("webOrigins"));
        }
      }

      ArrayNode groups = base.withArray("groups");
      for (JsonNode patchGroup : patch.withArray("groups")) {
        ObjectNode matchingBaseGroup = null;
        for (JsonNode baseGroup : groups) {
          if (baseGroup.path("name").equals(patchGroup.path("name"))) {
            matchingBaseGroup = (ObjectNode) baseGroup;
            break;
          }
        }
        if (matchingBaseGroup != null) {
          ArrayNode subGroups = matchingBaseGroup.withArray("subGroups");
          patchGroup.withArray("subGroups").forEach(subGroups::add);
        } else {
          groups.add(patchGroup);
        }
      }

      ArrayNode users = base.withArray("users");
      patch.withArray("users").forEach(users::add);

      File rendered = new File("target/test-classes/" + CLASSPATH_RESOURCE_NAME);
      rendered.getParentFile().mkdirs();
      mapper.writeValue(rendered, base);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to render the Keycloak e2e realm", e);
    }
  }
}
