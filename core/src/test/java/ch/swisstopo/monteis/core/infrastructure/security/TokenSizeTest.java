package ch.swisstopo.monteis.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * NFR3.1: an access token with all five roles, 25 read and 25 write experiment ids, signed like
 * Keycloak signs it (RS256, 2048-bit key), stays under 4 KB encoded - half the 8 KB default
 * request-header limit. The supported maximum per user was revised from 50 + 50 to 25 + 25
 * ids, because 50 + 50 encode to about 6.5 KB.
 */
class TokenSizeTest {

  private static final int MAX_ENCODED_BYTES = 4 * 1024;
  private static final int ASSIGNED_EXPERIMENTS = 25;

  @Test
  void should_keep_a_token_with_all_roles_and_25_plus_25_experiment_ids_under_4_kb()
      throws JOSEException, NoSuchAlgorithmException {
    // given
    List<String> writeIds = experimentIds();
    List<String> readIds = writeIds; // Keycloak aggregates write ids into the read ids too

    // when
    String encoded = sign(realisticAccessToken(readIds, writeIds));
    int encodedBytes = encoded.getBytes(StandardCharsets.US_ASCII).length;

    // then
    assertEquals(3, encoded.split("\\.").length, "compact JWS serialization");
    assertTrue(
        encodedBytes < MAX_ENCODED_BYTES,
        "encoded access token is " + encodedBytes + " bytes, limit " + MAX_ENCODED_BYTES);
  }

  private static List<String> experimentIds() {
    return IntStream.range(0, ASSIGNED_EXPERIMENTS)
        .mapToObj(i -> UUID.randomUUID().toString())
        .toList();
  }

  /** The claims a Keycloak access token for audience monteis-backend carries (contract C1). */
  private static JWTClaimsSet realisticAccessToken(List<String> readIds, List<String> writeIds) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer("https://sso.example.swisstopo.ch/auth/realms/monteis")
        .audience("monteis-backend")
        .subject(UUID.randomUUID().toString())
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(300)))
        .claim("typ", "Bearer")
        .claim("azp", "monteis-spa")
        .claim("sid", UUID.randomUUID().toString())
        .claim("acr", "1")
        .claim("scope", "openid profile email")
        .claim("email_verified", true)
        .claim("preferred_username", "firstname.lastname@swisstopo.ch")
        .claim(
            "monteis_access",
            Map.of(
                "roles",
                List.of(
                    "monteis-client:experiment:read",
                    "monteis-client:experiment:write",
                    "monteis-client:admin")))
        .claim("read_experiment_ids", readIds)
        .claim("write_experiment_ids", writeIds)
        .build();
  }

  private static String sign(JWTClaimsSet claims) throws JOSEException, NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    RSAPrivateKey privateKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID("sP8oYk1bF2mJ0y3pWQ7eX5c9vT4uZ6aN1dR8hL2gK0s")
                .build(),
            claims);
    jwt.sign(new RSASSASigner(privateKey));
    return jwt.serialize();
  }
}
