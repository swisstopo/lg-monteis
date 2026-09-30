package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonteisPrincipalTest {

  private static final UUID EXPERIMENT_A = UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_B = UUID.fromString("00000000-0000-7000-8000-000000000302");

  @Test
  void should_keep_write_ids_that_are_also_read_ids() {
    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.randomUUID(), "alice", List.of(EXPERIMENT_A, EXPERIMENT_B), List.of(EXPERIMENT_A));

    assertEquals(List.of(EXPERIMENT_A), principal.writeExperimentIds());
  }

  @Test
  void should_drop_write_ids_that_are_not_read_ids() {
    MonteisPrincipal principal =
        new MonteisPrincipal(
            UUID.randomUUID(), "alice", List.of(EXPERIMENT_A), List.of(EXPERIMENT_A, EXPERIMENT_B));

    assertEquals(List.of(EXPERIMENT_A), principal.writeExperimentIds());
  }

  @Test
  void should_drop_every_write_id_without_read_ids() {
    MonteisPrincipal principal =
        new MonteisPrincipal(UUID.randomUUID(), "bob", List.of(), List.of(EXPERIMENT_A));

    assertEquals(List.of(), principal.writeExperimentIds());
  }

  @Test
  void should_reject_broken_id_lists() {
    UUID subject = UUID.randomUUID();
    List<UUID> withNull = Arrays.asList(EXPERIMENT_A, null);

    assertThrows(
        NullPointerException.class,
        () -> new MonteisPrincipal(subject, "corrupt", withNull, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new MonteisPrincipal(subject, "corrupt", List.of(EXPERIMENT_A), null));
  }
}
