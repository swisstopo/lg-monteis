package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SystemSecurityContextTest {

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_bind_read_all_authority_while_action_runs() {
    // given
    AtomicReference<Authentication> captured = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(
        () -> captured.set(SecurityContextHolder.getContext().getAuthentication()));

    // then
    assertTrue(
        captured.get().getAuthorities().stream()
            .anyMatch(
                a ->
                    a.getAuthority().equals(MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY)));
  }

  @Test
  void should_bind_system_principal_with_empty_experiment_ids_while_action_runs() {
    // given
    AtomicReference<Authentication> captured = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(
        () -> captured.set(SecurityContextHolder.getContext().getAuthentication()));

    // then
    assertEquals(
        new MonteisPrincipal(
            UUID.fromString("00000000-0000-0000-0000-000000000000"), "SYSTEM", List.of()),
        captured.get().getPrincipal());
    assertEquals("SYSTEM", captured.get().getName());
  }

  @Test
  void should_restore_previous_context_after_action_completes() {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("previous", null, List.of()));
    Authentication before = SecurityContextHolder.getContext().getAuthentication();

    // when
    SystemSecurityContext.runAsSystem(() -> {});

    // then
    assertEquals(before, SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_not_leave_context_bound_after_action_completes_when_previously_unbound() {
    // given
    SecurityContextHolder.clearContext();

    // when
    SystemSecurityContext.runAsSystem(() -> {});

    // then
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
