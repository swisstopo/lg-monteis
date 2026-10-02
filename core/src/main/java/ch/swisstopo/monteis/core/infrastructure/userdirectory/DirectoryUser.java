package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DirectoryUser(UUID id, String firstName, String lastName, String email) {

  public static final Comparator<DirectoryUser> BY_NAME =
      Comparator.comparing(
              DirectoryUser::lastName, Comparator.nullsLast(String::compareToIgnoreCase))
          .thenComparing(
              DirectoryUser::firstName, Comparator.nullsLast(String::compareToIgnoreCase))
          .thenComparing(DirectoryUser::id);

  public String displayName() {
    return Stream.of(firstName, lastName)
        .filter(Objects::nonNull)
        .filter(name -> !name.isBlank())
        .collect(Collectors.joining(" "));
  }
}
