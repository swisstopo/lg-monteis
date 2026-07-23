package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.SecurityConfig;
import java.lang.annotation.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code @WebMvcTest} plus the {@link SecurityConfig} wiring and {@code test} profile every slice
 * test needs. Without {@code @ActiveProfiles("test")} these contexts fall back to the {@code prod}
 * profile (the default in {@code application.properties}), which requires real Keycloak env vars
 * and fails to resolve placeholders like {@code ${KC_JWK_SET_URI}}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public @interface ControllerTest {

  @AliasFor(annotation = WebMvcTest.class, attribute = "value")
  Class<?>[] value() default {};
}
