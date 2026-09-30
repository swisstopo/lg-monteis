package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.UNASSIGNED_EXPERIMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The capability matrix of {@link AccessPolicy} for the five privilege levels, plus the {@code
 * NONE} and {@code SYSTEM} sets and the fail-closed paths (NFR1.2, NFR1.3, NFR2.2, NFR6.2).
 */
class AccessPolicyTest {

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  /** level, readAll, writeAll, readable, editable, admin, documents. */
  static Stream<Arguments> matrix() {
    Set<UUID> scoped = Set.of(ASSIGNED_EXPERIMENT, OTHER_EXPERIMENT);
    return Stream.of(
        Arguments.of(PrivilegeLevel.BASISROLLE, false, Set.of(), Set.of(), false, false),
        Arguments.of(PrivilegeLevel.EXPERIMENT_USER, false, scoped, Set.of(), false, true),
        Arguments.of(
            PrivilegeLevel.EXPERIMENT_PI, false, scoped, Set.of(ASSIGNED_EXPERIMENT), false, true),
        Arguments.of(PrivilegeLevel.GLOBAL_EDITOR, true, Set.of(), Set.of(), false, false),
        Arguments.of(PrivilegeLevel.MONTEIS_ADMIN, true, Set.of(), Set.of(), true, true));
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void should_derive_the_capabilities_of_each_privilege_level(
      PrivilegeLevel level,
      boolean allExperiments,
      Set<UUID> readable,
      Set<UUID> editable,
      boolean admin,
      boolean documents) {
    // when
    Capabilities capabilities = AccessPolicy.capabilitiesOf(level.authentication());

    // then
    assertEquals(
        new Capabilities(
            allExperiments, allExperiments, readable, editable, admin, admin, documents, admin),
        capabilities);
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void should_answer_per_experiment_read_and_edit_for_each_privilege_level(
      PrivilegeLevel level,
      boolean allExperiments,
      Set<UUID> readable,
      Set<UUID> editable,
      boolean admin,
      boolean documents) {
    // when
    Capabilities capabilities = AccessPolicy.capabilitiesOf(level.authentication());

    // then
    for (UUID id : List.of(ASSIGNED_EXPERIMENT, OTHER_EXPERIMENT, UNASSIGNED_EXPERIMENT)) {
      assertEquals(allExperiments || readable.contains(id), capabilities.canReadExperiment(id));
      assertEquals(allExperiments || editable.contains(id), capabilities.canEditExperiment(id));
    }
    assertFalse(capabilities.canReadExperiment(null));
    assertFalse(capabilities.canEditExperiment(null));
  }

  @Test
  void should_return_none_without_authentication() {
    assertSame(Capabilities.NONE, AccessPolicy.capabilitiesOf(null));
  }

  @Test
  void should_return_none_for_an_unauthenticated_token() {
    // given
    Authentication unauthenticated =
        UsernamePasswordAuthenticationToken.unauthenticated(
            PrivilegeLevel.MONTEIS_ADMIN.authentication().getPrincipal(), null);

    // then
    assertSame(Capabilities.NONE, AccessPolicy.capabilitiesOf(unauthenticated));
  }

  @Test
  void should_return_none_for_a_foreign_principal_even_with_the_admin_authority() {
    // given: e.g. Spring Security Test's jwt() shortcut, which bypasses our converter
    Authentication foreign =
        UsernamePasswordAuthenticationToken.authenticated(
            "someone",
            null,
            List.of(new SimpleGrantedAuthority(MonteisAuthorities.ADMIN_AUTHORITY)));

    // then
    assertSame(Capabilities.NONE, AccessPolicy.capabilitiesOf(foreign));
  }

  @Test
  void should_return_none_when_reading_the_authentication_throws() {
    // given
    Authentication exploding = mock(Authentication.class);
    given(exploding.isAuthenticated()).willThrow(new IllegalStateException("boom"));

    // then
    assertSame(Capabilities.NONE, AccessPolicy.capabilitiesOf(exploding));
  }

  @Test
  void should_return_system_capabilities_only_while_run_as_system_is_bound() {
    // given
    AtomicReference<Capabilities> during = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(() -> during.set(boundCapabilities()));

    // then
    assertSame(Capabilities.SYSTEM, during.get());
    assertSame(Capabilities.SYSTEM, AccessPolicy.systemCapabilities());
    assertTrue(Capabilities.SYSTEM.canReadAllExperiments());
    assertFalse(Capabilities.SYSTEM.canWriteAllExperiments());
    assertFalse(Capabilities.SYSTEM.isAdmin());
    assertSame(Capabilities.NONE, boundCapabilities());
  }

  @Test
  void should_not_treat_a_look_alike_of_the_system_authentication_as_system() {
    // given: same subject and name as the system pseudo-user, but not the bound instance
    var lookAlike =
        new MonteisAuthenticationToken(
            null,
            new MonteisPrincipal(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "SYSTEM",
                List.of(),
                List.of()),
            List.of());

    // then
    assertEquals(Capabilities.NONE, AccessPolicy.capabilitiesOf(lookAlike));
  }

  @Test
  void should_drop_write_ids_that_are_not_also_read_ids() {
    // given
    var authentication =
        new MonteisAuthenticationToken(
            null,
            new MonteisPrincipal(
                UUID.randomUUID(),
                "tampered",
                List.of(OTHER_EXPERIMENT),
                List.of(OTHER_EXPERIMENT, UNASSIGNED_EXPERIMENT)),
            PrivilegeLevel.EXPERIMENT_PI.grantedAuthorities());

    // then
    assertEquals(
        Set.of(OTHER_EXPERIMENT),
        AccessPolicy.capabilitiesOf(authentication).editableExperimentIds());
  }

  @Test
  void should_ignore_write_ids_without_the_write_authority() {
    // given
    var authentication =
        new MonteisAuthenticationToken(
            null,
            new MonteisPrincipal(
                UUID.randomUUID(), "reader", List.of(OTHER_EXPERIMENT), List.of(OTHER_EXPERIMENT)),
            PrivilegeLevel.EXPERIMENT_USER.grantedAuthorities());

    // then
    assertFalse(AccessPolicy.capabilitiesOf(authentication).canEditExperiment(OTHER_EXPERIMENT));
  }

  @Test
  void should_only_read_the_authentication_and_decide_identically_every_time() {
    // given: the only collaborator is the Authentication itself - no repository, client or
    // cache exists that derivation could call (NFR6.2, NFR3.2)
    Authentication authentication = mock(Authentication.class);
    var principal = PrivilegeLevel.EXPERIMENT_PI.authentication().getPrincipal();
    given(authentication.isAuthenticated()).willReturn(true);
    given(authentication.getPrincipal()).willReturn(principal);
    given(authentication.getAuthorities())
        .willAnswer(invocation -> PrivilegeLevel.EXPERIMENT_PI.grantedAuthorities());
    List<Capabilities> results = new ArrayList<>();

    // when
    for (int i = 0; i < 3; i++) {
      results.add(AccessPolicy.capabilitiesOf(authentication));
    }

    // then
    assertEquals(1, Set.copyOf(results).size());
    then(authentication).should(times(3)).isAuthenticated();
    then(authentication).should(times(3)).getPrincipal();
    then(authentication).should(times(3)).getAuthorities();
    then(authentication).shouldHaveNoMoreInteractions();
  }

  private static Capabilities boundCapabilities() {
    return AccessPolicy.capabilitiesOf(SecurityContextHolder.getContext().getAuthentication());
  }
}
