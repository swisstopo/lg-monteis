package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Filter-chain adapter for {@code PUT} on {@link #PATH} and {@code POST} on {@link
 * #DOCUMENTS_PATH}: allows the request when {@link Capabilities} say the caller may write the
 * experiment named by the {@code id} path variable. A path id that is not a UUID identifies no
 * experiment and is denied. The {@code experiments_update} and {@code experiment_documents_insert}
 * RLS policies enforce the same rule in the database (defence in depth, ADR-003).
 */
class ExperimentWriteAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  private static final String EXPERIMENT_ID_VARIABLE = "id";
  static final String PATH = "/api/experiments/{" + EXPERIMENT_ID_VARIABLE + "}";
  static final String DOCUMENTS_PATH = PATH + "/documents";

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Capabilities capabilities = Capabilities.of(authentication.get());
    boolean canWrite =
        Uuids.tryParse(context.getVariables().get(EXPERIMENT_ID_VARIABLE))
            .map(capabilities::canWriteExperiment)
            .orElse(false);
    return new AuthorizationDecision(canWrite);
  }
}
