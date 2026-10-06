package ch.swisstopo.monteis.core.infrastructure.security;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Filter-chain adapter for {@code PUT} on {@link ApiPaths#EXPERIMENT} and {@code POST} on {@link
 * ApiPaths#EXPERIMENT_DOCUMENTS}: allows the request when {@link Capabilities} say the caller may
 * write the experiment named by the {@link ApiPaths#EXPERIMENT_ID} path variable. A path id that is
 * not a UUID identifies no experiment and is denied. The {@code experiments_update} and {@code
 * experiment_documents_insert} RLS policies enforce the same rule in the database (defence in
 * depth, ADR-003).
 */
class ExperimentWriteAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Capabilities capabilities = Capabilities.of(authentication.get());
    boolean canWrite =
        Uuids.tryParse(context.getVariables().get(ApiPaths.EXPERIMENT_ID))
            .map(capabilities::canWriteExperiment)
            .orElse(false);
    return new AuthorizationDecision(canWrite);
  }
}
