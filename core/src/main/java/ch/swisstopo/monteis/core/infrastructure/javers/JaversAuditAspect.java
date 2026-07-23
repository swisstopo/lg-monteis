package ch.swisstopo.monteis.core.infrastructure.javers;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.javers.core.Javers;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class JaversAuditAspect {

  private final Javers javers;

  public JaversAuditAspect(Javers javers) {
    this.javers = javers;
  }

  @AfterReturning(pointcut = "@annotation(AuditChanges)", returning = "result")
  public void auditReturnValue(Auditable result) {

    if (result == null) {
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUser = "SYSTEM";
    if (authentication != null
        && authentication.getPrincipal() instanceof MonteisPrincipal principal) {
      currentUser = principal.getSubject().toString();
    }

    javers.commit(currentUser, result);
  }
}
