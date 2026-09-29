package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Claim parsing of {@link KeycloakClaimExtractor}: malformed claims degrade to empty. */
class KeycloakClaimExtractorTest {

  private static final UUID SUBJECT = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final UUID EXPERIMENT_1 = UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_2 = UUID.fromString("00000000-0000-7000-8000-000000000302");

  @Test
  void should_read_identity_roles_and_experiment_ids() {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put("preferred_username", "alice");
    claims.put("monteis_access", Map.of("roles", List.of("monteis-client:experiment:read")));
    claims.put("read_experiment_ids", List.of(EXPERIMENT_1.toString(), EXPERIMENT_2.toString()));
    claims.put("write_experiment_ids", List.of(EXPERIMENT_1.toString()));

    // when
    KeycloakClaimExtractor extractor = KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims));

    // then
    assertEquals(SUBJECT, extractor.subject());
    assertEquals("alice", extractor.username());
    assertEquals(List.of("monteis-client:experiment:read"), extractor.roles());
    assertEquals(List.of(EXPERIMENT_1, EXPERIMENT_2), extractor.readExperimentIds());
    assertEquals(List.of(EXPERIMENT_1), extractor.writeExperimentIds());
  }

  @Test
  void should_yield_no_roles_when_any_role_is_not_a_string() {
    Map<String, Object> claims =
        Map.of("monteis_access", Map.of("roles", List.of("monteis-client:admin", 123)));

    assertEquals(List.of(), KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims)).roles());
  }

  @Test
  void should_yield_no_roles_when_roles_is_not_a_list() {
    Map<String, Object> claims = Map.of("monteis_access", Map.of("roles", "monteis-client:admin"));

    assertEquals(List.of(), KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims)).roles());
  }

  @Test
  void should_yield_no_roles_when_the_monteis_access_claim_is_missing_or_not_an_object() {
    assertEquals(List.of(), KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), Map.of())).roles());
    assertEquals(
        List.of(),
        KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), Map.of("monteis_access", "admin")))
            .roles());
  }

  @Test
  void should_drop_non_uuid_null_and_duplicate_experiment_ids_one_by_one() {
    // given
    Map<String, Object> claims = new HashMap<>();
    claims.put(
        "read_experiment_ids",
        Arrays.asList(
            EXPERIMENT_1.toString(),
            "not-a-uuid",
            42,
            null,
            EXPERIMENT_2.toString(),
            EXPERIMENT_1.toString()));

    // then
    assertEquals(
        List.of(EXPERIMENT_1, EXPERIMENT_2),
        KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims)).readExperimentIds());
  }

  @Test
  void should_yield_no_experiment_ids_when_the_claims_are_missing_or_not_lists() {
    // given
    Map<String, Object> claims = Map.of("write_experiment_ids", EXPERIMENT_1.toString());
    KeycloakClaimExtractor extractor = KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims));

    // then
    assertEquals(List.of(), extractor.readExperimentIds());
    assertEquals(List.of(), extractor.writeExperimentIds());
  }

  @Test
  void should_ignore_the_removed_experiment_ids_claim() {
    Map<String, Object> claims = Map.of("experiment_ids", List.of(EXPERIMENT_1.toString()));

    assertEquals(
        List.of(),
        KeycloakClaimExtractor.from(jwt(SUBJECT.toString(), claims)).readExperimentIds());
  }

  @Test
  void should_reject_a_subject_that_is_not_a_uuid() {
    KeycloakClaimExtractor extractor = KeycloakClaimExtractor.from(jwt("not-a-uuid", Map.of()));

    assertThrows(IllegalArgumentException.class, extractor::subject);
  }

  private static Jwt jwt(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Jwt.Builder builder =
        Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60));
    claims.forEach(builder::claim);
    return builder.build();
  }
}
