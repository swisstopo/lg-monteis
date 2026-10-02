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
import java.util.EnumSet;
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
 * The privilege rules of {@link Capabilities}, straight from grants and assigned ids, and what
 * {@link Capabilities#of} yields end to end for the five privilege levels, the {@code NONE} and
 * {@code SYSTEM} sets and the fail-closed paths (NFR1.2, NFR1.3, NFR2.2, NFR6.2).
 */
class CapabilitiesTest {

  @AfterEach
  void clearSecurityContextHolder() {
    SecurityContextHolder.clearContext();
  }

  /** level, readAll, writeAll, readable, writable, admin, documents. */
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
      Set<UUID> writable,
      boolean admin,
      boolean documents) {
    // when
    Capabilities capabilities = Capabilities.of(level.authentication());

    // then
    assertEquals(allExperiments, capabilities.canReadAllExperiments());
    assertEquals(allExperiments, capabilities.canWriteAllExperiments());
    assertEquals(readable, capabilities.readableExperimentIds());
    assertEquals(writable, capabilities.writableExperimentIds());
    assertEquals(documents, capabilities.canAccessDocuments());
    assertEquals(admin, capabilities.isAdmin());
    assertEquals(admin, capabilities.canCreateExperiment());
    assertEquals(admin, capabilities.canManageSensors());
    assertEquals(admin, capabilities.canManageExperimentOwners());
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void should_answer_per_experiment_read_and_write_for_each_privilege_level(
      PrivilegeLevel level,
      boolean allExperiments,
      Set<UUID> readable,
      Set<UUID> writable,
      boolean admin,
      boolean documents) {
    // when
    Capabilities capabilities = Capabilities.of(level.authentication());

    // then
    for (UUID id : List.of(ASSIGNED_EXPERIMENT, OTHER_EXPERIMENT, UNASSIGNED_EXPERIMENT)) {
      assertEquals(allExperiments || readable.contains(id), capabilities.canReadExperiment(id));
      assertEquals(allExperiments || writable.contains(id), capabilities.canWriteExperiment(id));
    }
    assertFalse(capabilities.canReadExperiment(null));
    assertFalse(capabilities.canWriteExperiment(null));
  }

  @Test
  void should_return_none_without_authentication() {
    assertSame(Capabilities.NONE, Capabilities.of(null));
  }

  @Test
  void should_return_none_for_an_unauthenticated_token() {
    // given
    Authentication unauthenticated =
        UsernamePasswordAuthenticationToken.unauthenticated(
            PrivilegeLevel.MONTEIS_ADMIN.authentication().getPrincipal(), null);

    // then
    assertSame(Capabilities.NONE, Capabilities.of(unauthenticated));
  }

  @Test
  void should_return_none_for_a_foreign_principal_even_with_the_admin_authority() {
    // given: e.g. Spring Security Test's jwt() shortcut, which bypasses our converter
    Authentication foreign =
        UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of(Grant.ADMIN));

    // then
    assertSame(Capabilities.NONE, Capabilities.of(foreign));
  }

  @Test
  void should_return_none_when_reading_the_authentication_throws() {
    // given
    Authentication exploding = mock(Authentication.class);
    given(exploding.isAuthenticated()).willThrow(new IllegalStateException("boom"));

    // then
    assertSame(Capabilities.NONE, Capabilities.of(exploding));
  }

  @Test
  void should_return_system_capabilities_only_while_run_as_system_is_bound() {
    // given
    AtomicReference<Capabilities> during = new AtomicReference<>();

    // when
    SystemSecurityContext.runAsSystem(() -> during.set(boundCapabilities()));

    // then
    assertSame(Capabilities.SYSTEM, during.get());
    assertTrue(Capabilities.SYSTEM.canReadAllExperiments());
    assertFalse(Capabilities.SYSTEM.canWriteAllExperiments());
    assertFalse(Capabilities.SYSTEM.isAdmin());
    assertSame(Capabilities.NONE, boundCapabilities());
  }

  @Test
  void should_map_each_authority_name_to_its_grant_and_ignore_unknown_ones() {
    // given: plain authorities with the api:* names, not the Grant constants themselves
    var authentication =
        new MonteisAuthenticationToken(
            null,
            new MonteisPrincipal(UUID.randomUUID(), "all", List.of(), List.of()),
            List.of(
                new SimpleGrantedAuthority("api:experiment:read"),
                new SimpleGrantedAuthority("api:experiment:write"),
                new SimpleGrantedAuthority("api:experiment:write-all"),
                new SimpleGrantedAuthority("api:documents:read"),
                new SimpleGrantedAuthority("api:admin"),
                new SimpleGrantedAuthority("api:unknown")));

    // then: every grant except SYSTEM_READ_ALL, which no authority maps to
    assertEquals(
        EnumSet.complementOf(EnumSet.of(Grant.SYSTEM_READ_ALL)),
        Capabilities.of(authentication).grants());
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
    assertEquals(Capabilities.NONE, Capabilities.of(lookAlike));
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
    assertEquals(Set.of(OTHER_EXPERIMENT), Capabilities.of(authentication).writableExperimentIds());
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
    assertFalse(Capabilities.of(authentication).canWriteExperiment(OTHER_EXPERIMENT));
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
      results.add(Capabilities.of(authentication));
    }

    // then
    assertEquals(1, Set.copyOf(results).size());
    then(authentication).should(times(3)).isAuthenticated();
    then(authentication).should(times(3)).getPrincipal();
    then(authentication).should(times(3)).getAuthorities();
    then(authentication).shouldHaveNoMoreInteractions();
  }

  private static Capabilities boundCapabilities() {
    return Capabilities.of(SecurityContextHolder.getContext().getAuthentication());
  }

  // --- rules, without Spring Security ---

  private static final UUID EXPERIMENT_A = UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_B = UUID.fromString("00000000-0000-7000-8000-000000000302");
  private static final Set<UUID> BOTH = Set.of(EXPERIMENT_A, EXPERIMENT_B);

  private static Capabilities capabilities(
      Set<Grant> grants, Set<UUID> readIds, Set<UUID> writeIds) {
    return new Capabilities(grants, readIds, writeIds);
  }

  @Test
  void should_grant_nothing_without_grants() {
    Capabilities none = Capabilities.NONE;

    assertFalse(none.isAdmin());
    assertFalse(none.canReadAllExperiments());
    assertFalse(none.canWriteAllExperiments());
    assertFalse(none.canCreateExperiment());
    assertFalse(none.canManageSensors());
    assertFalse(none.canManageExperimentOwners());
    assertFalse(none.canAccessDocuments());
    assertFalse(none.canReadExperiment(EXPERIMENT_A));
    assertFalse(none.canWriteExperiment(EXPERIMENT_A));
  }

  @Test
  void should_let_an_admin_do_everything_but_access_documents_on_its_own() {
    Capabilities admin = capabilities(Set.of(Grant.ADMIN), Set.of(), Set.of());

    assertTrue(admin.isAdmin());
    assertTrue(admin.canReadAllExperiments());
    assertTrue(admin.canWriteAllExperiments());
    assertTrue(admin.canCreateExperiment());
    assertTrue(admin.canManageSensors());
    assertTrue(admin.canManageExperimentOwners());
    assertFalse(admin.canAccessDocuments());
    assertTrue(admin.canWriteExperiment(EXPERIMENT_A));
  }

  @Test
  void should_let_a_global_editor_read_and_write_every_experiment_without_admin_functions() {
    Capabilities editor = capabilities(Set.of(Grant.EXPERIMENT_WRITE_ALL), Set.of(), Set.of());

    assertTrue(editor.canReadAllExperiments());
    assertTrue(editor.canWriteAllExperiments());
    assertTrue(editor.canWriteExperiment(EXPERIMENT_B));
    assertFalse(editor.isAdmin());
    assertFalse(editor.canCreateExperiment());
    assertFalse(editor.canManageSensors());
    assertFalse(editor.canManageExperimentOwners());
  }

  @Test
  void should_let_the_system_context_read_every_experiment_but_write_none() {
    Capabilities system = Capabilities.SYSTEM;

    assertTrue(system.canReadAllExperiments());
    assertTrue(system.canReadExperiment(EXPERIMENT_A));
    assertFalse(system.canWriteAllExperiments());
    assertFalse(system.canWriteExperiment(EXPERIMENT_A));
    assertEquals(Set.of(), system.writableExperimentIds());
    assertFalse(system.isAdmin());
  }

  @Test
  void should_scope_reads_and_writes_to_the_assigned_experiments() {
    Capabilities pi =
        capabilities(
            Set.of(Grant.EXPERIMENT_READ, Grant.EXPERIMENT_WRITE), BOTH, Set.of(EXPERIMENT_A));

    assertEquals(BOTH, pi.readableExperimentIds());
    assertEquals(Set.of(EXPERIMENT_A), pi.writableExperimentIds());
    assertTrue(pi.canWriteExperiment(EXPERIMENT_A));
    assertFalse(pi.canWriteExperiment(EXPERIMENT_B));
    assertTrue(pi.canReadExperiment(EXPERIMENT_B));
  }

  @Test
  void should_ignore_assigned_write_ids_without_the_write_grant() {
    Capabilities reader = capabilities(Set.of(Grant.EXPERIMENT_READ), BOTH, Set.of(EXPERIMENT_A));

    assertEquals(Set.of(), reader.writableExperimentIds());
    assertFalse(reader.canWriteExperiment(EXPERIMENT_A));
  }

  @Test
  void should_leave_the_id_sets_empty_when_an_all_experiments_rule_applies() {
    Capabilities editor =
        capabilities(
            Set.of(Grant.EXPERIMENT_WRITE_ALL, Grant.EXPERIMENT_WRITE), BOTH, Set.of(EXPERIMENT_A));

    assertEquals(Set.of(), editor.readableExperimentIds());
    assertEquals(Set.of(), editor.writableExperimentIds());
  }

  @Test
  void should_never_allow_a_null_experiment_id() {
    Capabilities admin = capabilities(Set.of(Grant.ADMIN), Set.of(), Set.of());

    assertFalse(admin.canReadExperiment(null));
    assertFalse(admin.canWriteExperiment(null));
  }

  @Test
  void should_answer_documents_from_its_own_grant() {
    assertTrue(capabilities(Set.of(Grant.DOCUMENTS_READ), Set.of(), Set.of()).canAccessDocuments());
  }
}
