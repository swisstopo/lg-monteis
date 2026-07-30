package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record SensorType(Long id, String name, Integer version) {

  public SensorType {
    name = normalize(name);
  }

  private static String normalize(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Type name must not be blank");
    }

    return Arrays.stream(name.trim().split("\\s+"))
        .map(SensorType::capitalize)
        .collect(Collectors.joining(" "));
  }

  private static String capitalize(String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
  }
}
