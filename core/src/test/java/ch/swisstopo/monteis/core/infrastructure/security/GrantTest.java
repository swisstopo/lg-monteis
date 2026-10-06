package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GrantTest {

  @ParameterizedTest
  @EnumSource(value = Grant.class, names = "SYSTEM_READ_ALL", mode = EnumSource.Mode.EXCLUDE)
  void should_find_every_request_grant_by_its_authority_name(Grant grant) {
    assertEquals(Optional.of(grant), Grant.fromAuthority(grant.getAuthority()));
  }

  @Test
  void should_give_the_system_grant_no_authority_name_so_no_token_can_name_it() {
    assertNull(Grant.SYSTEM_READ_ALL.getAuthority());
    assertEquals(Optional.empty(), Grant.fromAuthority(null));
    assertEquals(Optional.empty(), Grant.fromAuthority("SYSTEM_READ_ALL"));
  }

  @Test
  void should_find_no_grant_for_an_unknown_or_legacy_name() {
    assertEquals(Optional.empty(), Grant.fromAuthority("api:unknown"));
    assertEquals(Optional.empty(), Grant.fromAuthority("api:experiment:read-all"));
  }
}
