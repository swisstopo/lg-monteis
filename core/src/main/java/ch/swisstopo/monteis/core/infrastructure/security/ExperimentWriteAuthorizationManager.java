package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authorizes a write on the experiment named by the {@code id} path variable of {@link #PATH}:
 * admins and {@code api:experiment:write-all} may write any experiment, {@code
 * api:experiment:write} only the ones in {@link MonteisPrincipal#writeExperimentIds()}. The {@code
 * experiments_update} RLS policy (V15) enforces the same rule in the database.
 */
class ExperimentWriteAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  private static final String EXPERIMENT_ID_VARIABLE = "id";
  static final String PATH = "/api/experiments/{" + EXPERIMENT_ID_VARIABLE + "}";

  private static boolean canWrite(
      Authentication authentication, RequestAuthorizationContext context) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (AuthorityChecks.canWriteAllExperiments(authentication)) {
      return true;
    }
    boolean canWriteScoped =
        AuthorityChecks.hasAuthority(authentication, EXPERIMENT_WRITE_AUTHORITY);

    if (!canWriteScoped || !(authentication.getPrincipal() instanceof MonteisPrincipal principal)) {
      return false;
    }
    Optional<UUID> experimentId =
        Uuids.tryParse(context.getVariables().get(EXPERIMENT_ID_VARIABLE));
    return experimentId.filter(principal.getWriteExperimentIds()::contains).isPresent();
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    return new AuthorizationDecision(canWrite(authentication.get(), context));
  }
}
