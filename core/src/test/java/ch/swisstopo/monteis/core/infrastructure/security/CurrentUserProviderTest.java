package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserProviderTest {

  private final CurrentUserProvider provider = new CurrentUserProvider();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_return_subject_and_username_of_the_monteis_principal() {
    // given
    UUID subject = UUID.randomUUID();
    MonteisPrincipal principal = new MonteisPrincipal(subject, "alice", List.of(), List.of());

    SecurityContextHolder.getContext()
        .setAuthentication(new MonteisAuthenticationToken(null, principal, List.of()));

    // when / then
    assertAll(
        () -> assertEquals(subject.toString(), provider.getCurrentUserHandle()),
        () -> assertEquals("alice", provider.requireCurrentUsername()));
  }

  @Test
  void should_have_no_user_without_authentication() {
    assertAll(
        () -> assertNull(provider.getCurrentUserHandle()),
        () -> assertThrows(IllegalStateException.class, provider::requireCurrentUsername));
  }

  @Test
  void should_have_no_user_for_another_principal_type() {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("someone", "secret"));

    // when / then
    assertAll(
        () -> assertNull(provider.getCurrentUserHandle()),
        () -> assertThrows(IllegalStateException.class, provider::requireCurrentUsername));
  }
}
