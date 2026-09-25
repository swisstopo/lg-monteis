package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

final class Uuids {

  private Uuids() {}

  static Optional<UUID> tryParse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
