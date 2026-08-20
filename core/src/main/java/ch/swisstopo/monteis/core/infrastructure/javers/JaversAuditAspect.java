package ch.swisstopo.monteis.core.infrastructure.javers;

import ch.swisstopo.monteis.core.infrastructure.security.SecurityUtils;
import java.util.Optional;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.javers.core.Javers;
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

    String currentUser = Optional.ofNullable(SecurityUtils.getCurrentUserHandle()).orElse("SYSTEM");

    javers.commit(currentUser, result);
  }
}
