package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SystemSecurityContextTest {

  @Test
  void should_bind_admin_access_level_while_action_runs() {
    // given
    AtomicReference<SecurityContext> captured = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(() -> captured.set(SecurityContext.current()));

    // then
    assertEquals(AccessLevel.ADMIN, captured.get().accessLevel());
  }

  @Test
  void should_bind_empty_experiment_ids_while_action_runs() {
    // given
    AtomicReference<SecurityContext> captured = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(() -> captured.set(SecurityContext.current()));

    // then
    assertTrue(captured.get().experimentIds().isEmpty());
  }

  @Test
  void should_not_leave_context_bound_after_action_completes() {
    // given / when
    SystemSecurityContext.runAsSystem(() -> {});

    // then
    assertEquals(SecurityContext.DENY_ALL, SecurityContext.current());
  }
}
