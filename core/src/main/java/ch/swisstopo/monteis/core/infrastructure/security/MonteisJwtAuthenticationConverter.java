package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;

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
        hasAuthority(authorities, EXPERIMENT_READ_AUTHORITY)
            ? claims.readExperimentIds()
            : List.of();
    // MonteisPrincipal drops write ids outside the read ids
    List<UUID> writeExperimentIds =
        hasAuthority(authorities, EXPERIMENT_WRITE_AUTHORITY)
            ? claims.writeExperimentIds()
            : List.of();

    MonteisPrincipal principal =
        new MonteisPrincipal(subject, claims.username(), readExperimentIds, writeExperimentIds);

    return new MonteisAuthenticationToken(source, principal, authorities);
  }

  // which experiment id claims to trust is decided while building the token, not by Capabilities
  private static boolean hasAuthority(
      Collection<? extends GrantedAuthority> authorities, String authority) {
    return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }
}
