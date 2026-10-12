package ch.swisstopo.monteis.core.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakGroup(
    String id, String path, Map<String, List<String>> attributes, List<KeycloakGroup> subGroups) {

  Stream<KeycloakGroup> selfAndAllSubgroups() {
    Stream<KeycloakGroup> descendants =
        subGroups == null
            ? Stream.empty()
            : subGroups.stream().flatMap(KeycloakGroup::selfAndAllSubgroups);
    return Stream.concat(Stream.of(this), descendants);
  }

  boolean hasAttributeValue(String name, String value) {
    return attributes != null && attributes.getOrDefault(name, List.of()).contains(value);
  }
}
