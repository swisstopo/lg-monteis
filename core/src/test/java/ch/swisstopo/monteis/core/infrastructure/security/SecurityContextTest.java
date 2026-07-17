package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityContextTest {

  @Test
  void should_force_empty_experiment_ids_when_access_level_is_none() {
    // given
    Set<Long> requestedExperimentIds = Set.of(1L, 2L);

    // when
    SecurityContext context = new SecurityContext(AccessLevel.NONE, requestedExperimentIds);

    // then
    assertTrue(context.experimentIds().isEmpty(), "NONE must never carry experiment ids");
  }

  @Test
  void should_keep_experiment_ids_when_access_level_is_user() {
    // given
    Set<Long> experimentIds = Set.of(1L, 2L);

    // when
    SecurityContext context = new SecurityContext(AccessLevel.USER, experimentIds);

    // then
    assertEquals(experimentIds, context.experimentIds());
  }

  @Test
  void should_keep_experiment_ids_when_access_level_is_admin() {
    // given
    Set<Long> experimentIds = Set.of(5L);

    // when
    SecurityContext context = new SecurityContext(AccessLevel.ADMIN, experimentIds);

    // then
    assertEquals(experimentIds, context.experimentIds());
  }

  @Test
  void deny_all_should_be_none_with_no_experiment_ids() {
    // given / when
    SecurityContext denyAll = SecurityContext.DENY_ALL;

    // then
    assertEquals(AccessLevel.NONE, denyAll.accessLevel());
    assertTrue(denyAll.experimentIds().isEmpty());
  }

  @Test
  void current_should_return_deny_all_when_unbound() {
    // given / when
    SecurityContext current = SecurityContext.current();

    // then
    assertEquals(SecurityContext.DENY_ALL, current);
  }

  @Test
  void current_should_return_bound_value_within_scoped_value_where() {
    // given
    SecurityContext bound = new SecurityContext(AccessLevel.USER, Set.of(7L));

    // when / then
    ScopedValue.where(SecurityContext.CURRENT, bound)
        .run(() -> assertEquals(bound, SecurityContext.current()));
  }
}
