package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AccessLevelTest {

  @Test
  void should_expose_role_name_via_toString_for_admin() {
    // given / when
    String roleName = AccessLevel.ADMIN.toString();

    // then
    assertEquals("ADMIN", roleName);
  }

  @Test
  void should_expose_role_name_via_toString_for_user() {
    // given / when
    String roleName = AccessLevel.USER.toString();

    // then
    assertEquals("USER", roleName);
  }

  @Test
  void should_expose_role_name_via_toString_for_none() {
    // given / when
    String roleName = AccessLevel.NONE.toString();

    // then
    assertEquals("NONE", roleName);
  }
}
