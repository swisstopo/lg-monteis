package ch.swisstopo.monteis.core.infrastructure.javers;

import java.lang.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional
public @interface AuditChanges {}
