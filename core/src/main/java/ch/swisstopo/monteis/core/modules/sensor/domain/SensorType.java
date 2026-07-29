package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record SensorType(Long id, String type, Integer version) {
  public SensorType {
    if (type != null) {
      type =
          Arrays.stream(type.trim().split("\\s+"))
              .map(
                  word ->
                      Character.toUpperCase(word.charAt(0))
                          + word.substring(1).toLowerCase(Locale.ROOT))
              .collect(Collectors.joining(" "));
    }
  }
}
