package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Builds this app's {@link MonteisAuthenticationToken} from a JWT: client roles map to {@code
 * api:*} authorities via {@link GrantedAuthorityExtractor}, and the identity and experiment ids
 * read by {@link KeycloakClaimExtractor} become the {@link MonteisPrincipal}.
 */
public class MonteisJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final GrantedAuthorityExtractor grantedAuthorityExtractor =
      new GrantedAuthorityExtractor();

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt source) {
    KeycloakClaimExtractor claims = KeycloakClaimExtractor.from(source);
    UUID subject = claims.subject();
    Collection<GrantedAuthority> authorities =
        grantedAuthorityExtractor.extract(subject, claims.roles());

    // Fail closed: a caller without the matching authority must never leak a populated
    // experiment id claim through as if it were a legitimately scoped user.
    List<UUID> readExperimentIds =
        AccessPolicy.grantsScopedExperimentRead(authorities)
            ? claims.readExperimentIds()
            : List.of();
    // Keycloak maps write_experiment_ids into read_experiment_ids too, so write ids outside the
    // read ids mean a tampered or misconfigured token - drop them rather than trust them.
    List<UUID> writeExperimentIds =
        AccessPolicy.grantsScopedExperimentWrite(authorities)
            ? claims.writeExperimentIds().stream().filter(readExperimentIds::contains).toList()
            : List.of();

    MonteisPrincipal principal =
        new MonteisPrincipal(subject, claims.username(), readExperimentIds, writeExperimentIds);

    return new MonteisAuthenticationToken(source, principal, authorities);
  }
}
