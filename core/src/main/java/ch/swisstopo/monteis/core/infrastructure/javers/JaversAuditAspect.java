package ch.swisstopo.monteis.core.infrastructure.javers;

import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import java.util.Optional;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.javers.core.Javers;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class JaversAuditAspect {

  private final Javers javers;
  private final CurrentUserProvider currentUserProvider;

  public JaversAuditAspect(Javers javers, CurrentUserProvider currentUserProvider) {
    this.javers = javers;
    this.currentUserProvider = currentUserProvider;
  }

  @AfterReturning(pointcut = "@annotation(AuditChanges)", returning = "result")
  public void auditReturnValue(Auditable result) {

    if (result == null) {
      return;
    }

    @SuppressWarnings("java:S2325")
    String currentUser =
        Optional.ofNullable(currentUserProvider.getCurrentUserHandle()).orElse("SYSTEM");

    javers.commit(currentUser, result);
  }
}
