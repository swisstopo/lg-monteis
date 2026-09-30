package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.swisstopo.monteis.core.itconfig.LogCapture;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/** The 1:1 role mapping and the fail-closed role rules of {@link GrantedAuthorityExtractor}. */
class GrantedAuthorityExtractorTest {

  private static final UUID SUBJECT = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private final GrantedAuthorityExtractor extractor = new GrantedAuthorityExtractor();

  @ParameterizedTest
  @EnumSource(PrivilegeLevel.class)
  void should_map_the_roles_of_each_privilege_level_to_its_authorities(PrivilegeLevel level) {
    assertEquals(Set.copyOf(level.grantedAuthorities()), extractor.extract(SUBJECT, level.roles()));
  }

  @Test
  void should_map_each_of_the_five_roles_one_to_one() {
    assertEquals(
        Set.of(
            "api:experiment:read",
            "api:experiment:write",
            "api:experiment:write-all",
            "api:documents:read",
            "api:admin"),
        names(
            extractor.extract(
                SUBJECT,
                List.of(
                    "monteis-client:experiment:read",
                    "monteis-client:experiment:write",
                    "monteis-client:experiment:write:all",
                    "monteis-client:documents:read",
                    "monteis-client:admin"))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "monteis-client:read",
        "monteis-client:read-all",
        "monteis-client:write",
        "monteis-client:experiment:read:all",
        "experiment:read",
        "offline_access"
      })
  void should_ignore_legacy_and_unknown_roles(String role) {
    assertEquals(Set.of(), extractor.extract(SUBJECT, List.of(role)));
  }

  @Test
  void should_not_let_a_legacy_read_all_role_widen_a_scoped_reader() {
    assertEquals(
        Set.of("api:experiment:read"),
        names(
            extractor.extract(
                SUBJECT,
                List.of("monteis-client:experiment:read", "monteis-client:experiment:read:all"))));
  }

  @Test
  void should_reject_experiment_write_without_experiment_read() {
    OAuth2AuthenticationException exception =
        assertThrows(
            OAuth2AuthenticationException.class,
            () -> extractor.extract(SUBJECT, List.of("monteis-client:experiment:write")));

    assertEquals(OAuth2ErrorCodes.INVALID_TOKEN, exception.getError().getErrorCode());
  }

  @Test
  void should_reject_experiment_write_without_read_even_alongside_write_all() {
    assertThrows(
        OAuth2AuthenticationException.class,
        () ->
            extractor.extract(
                SUBJECT,
                List.of("monteis-client:experiment:write", "monteis-client:experiment:write:all")));
  }

  @Test
  void should_accept_write_all_alone_since_it_implies_read_on_every_experiment() {
    assertEquals(
        Set.of("api:experiment:write-all"),
        names(extractor.extract(SUBJECT, List.of("monteis-client:experiment:write:all"))));
  }

  @Test
  void should_grant_nothing_for_no_roles() {
    assertEquals(Set.of(), extractor.extract(SUBJECT, List.of()));
  }

  @Test
  void should_log_a_rejection_at_warn_with_sub_and_role_names_only() {
    try (LogCapture logs = LogCapture.of(GrantedAuthorityExtractor.class, Level.WARN)) {
      // when
      assertThrows(
          OAuth2AuthenticationException.class,
          () ->
              extractor.extract(
                  SUBJECT,
                  List.of("monteis-client:experiment:write", "monteis-client:documents:read")));

      // then: one line naming the sub and the offending roles, nothing else from the token
      assertEquals(1, logs.messages().size());
      String line = logs.messages().getFirst();
      assertTrue(line.contains(SUBJECT.toString()), line);
      assertTrue(line.contains("monteis-client:experiment:write"), line);
      assertTrue(line.contains("monteis-client:experiment:read"), line);
      assertFalse(line.contains("documents"), line);
    }
  }

  private static Set<String> names(Set<GrantedAuthority> authorities) {
    return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
  }
}
