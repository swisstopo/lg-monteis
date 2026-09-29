package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The transitional {@link AuthorityChecks} delegates answer exactly what {@link AccessPolicy}
 * answers, so the RLS session values of {@code RlsConnectionProvider} follow the new rules (review
 * R-01).
 */
class AuthorityChecksTest {

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_delegate_all_experiment_checks_to_access_policy(PrivilegeLevel level) {
    // given
    Authentication authentication = level.authentication();
    Capabilities capabilities = AccessPolicy.capabilitiesOf(authentication);

    // then
    assertEquals(
        capabilities.canReadAllExperiments(),
        AuthorityChecks.canReadAllExperiments(authentication));
    assertEquals(
        capabilities.canWriteAllExperiments(),
        AuthorityChecks.canWriteAllExperiments(authentication));
  }

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_trust_read_ids_exactly_for_levels_with_the_read_authority(PrivilegeLevel level) {
    assertEquals(
        !level.readExperimentIds().isEmpty(),
        AuthorityChecks.canReadAnyExperiment(level.grantedAuthorities()));
  }

  @Test
  void should_grant_all_experiments_only_to_admin_and_global_editor() {
    assertTrue(
        AuthorityChecks.canReadAllExperiments(PrivilegeLevel.MONTEIS_ADMIN.authentication()));
    assertTrue(
        AuthorityChecks.canWriteAllExperiments(PrivilegeLevel.GLOBAL_EDITOR.authentication()));
    assertFalse(
        AuthorityChecks.canReadAllExperiments(PrivilegeLevel.EXPERIMENT_PI.authentication()));
    assertFalse(
        AuthorityChecks.canWriteAllExperiments(PrivilegeLevel.EXPERIMENT_PI.authentication()));
  }

  @Test
  void should_give_the_system_context_read_all_but_not_write_all() {
    AtomicBoolean readAll = new AtomicBoolean();
    AtomicBoolean writeAll = new AtomicBoolean(true);

    SystemSecurityContext.runAsSystem(
        () -> {
          Authentication system = SecurityContextHolder.getContext().getAuthentication();
          readAll.set(AuthorityChecks.canReadAllExperiments(system));
          writeAll.set(AuthorityChecks.canWriteAllExperiments(system));
        });

    assertTrue(readAll.get());
    assertFalse(writeAll.get());
  }

  @Test
  void should_fail_closed_for_null_and_foreign_authentications() {
    Authentication foreign =
        UsernamePasswordAuthenticationToken.authenticated(
            "x", null, List.of(new SimpleGrantedAuthority(MonteisAuthorities.ADMIN_AUTHORITY)));

    assertFalse(AuthorityChecks.canReadAllExperiments(null));
    assertFalse(AuthorityChecks.canWriteAllExperiments(null));
    assertFalse(AuthorityChecks.canReadAllExperiments(foreign));
    assertFalse(AuthorityChecks.canWriteAllExperiments(foreign));
  }

  @Test
  void should_check_single_authorities_on_an_authentication_and_on_a_collection() {
    Authentication admin = PrivilegeLevel.MONTEIS_ADMIN.authentication();

    assertTrue(AuthorityChecks.hasAuthority(admin, MonteisAuthorities.ADMIN_AUTHORITY));
    assertFalse(
        AuthorityChecks.hasAuthority(admin, MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY));
    assertFalse(AuthorityChecks.hasAuthority(List.of(), MonteisAuthorities.ADMIN_AUTHORITY));
  }
}
