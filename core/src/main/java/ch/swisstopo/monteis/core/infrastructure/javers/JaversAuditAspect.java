package ch.swisstopo.monteis.core.infrastructure.javers;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.javers.core.Javers;
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

    String currentUser = "SYSTEM";
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
    }

    javers.commit(currentUser, result);
  }
}
