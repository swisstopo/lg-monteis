package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
  void should_bind_an_authentication_that_access_policy_answers_with_system_capabilities() {
    // given
    AtomicReference<Authentication> captured = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(
        () -> captured.set(SecurityContextHolder.getContext().getAuthentication()));

    // then: all-experiment read and nothing else, without any authority of its own
    assertSame(Capabilities.SYSTEM, Capabilities.of(captured.get()));
    assertTrue(captured.get().getAuthorities().isEmpty());
    assertTrue(SystemSecurityContext.isSystemAuthentication(captured.get()));
  }

  @Test
  void should_not_recognise_other_authentications_as_system() {
    assertFalse(SystemSecurityContext.isSystemAuthentication(null));
    assertFalse(
        SystemSecurityContext.isSystemAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("SYSTEM", null, List.of())));
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
            UUID.fromString("00000000-0000-0000-0000-000000000000"),
            "SYSTEM",
            List.of(),
            List.of()),
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
