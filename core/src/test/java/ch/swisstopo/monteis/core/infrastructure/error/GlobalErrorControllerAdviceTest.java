package ch.swisstopo.monteis.core.infrastructure.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.jooq.exception.DataChangedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ControllerTest
@ContextConfiguration(
    classes = {
      GlobalErrorControllerAdviceTest.DummyController.class,
      GlobalErrorControllerAdvice.class
    })
@Import(GlobalErrorControllerAdvice.class)
class GlobalErrorControllerAdviceTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void should_translate_object_business_validation_exception_return_422() throws Exception {
    // given a request to an endpoint that throws an ObjectBusinessValidationException

    // when
    var response = mockMvc.perform(get("/dummy/object-error").with(jwt()));

    // then
    response
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.messageKey").value("object.invalid"))
        .andExpect(
            jsonPath("$.field")
                .doesNotExist()) // Proves it is mapped as a form-level error, not a field error
        .andExpect(jsonPath("$.actualValue").doesNotExist());
  }

  @Test
  void should_translate_field_business_validation_exception_return_422() throws Exception {
    // given a request to an endpoint that throws a FieldBusinessValidationException

    // when
    var response = mockMvc.perform(get("/dummy/field-error").with(jwt()));

    // then
    response
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.field").value("code"))
        .andExpect(jsonPath("$.actualValue").value("DUP-01"))
        .andExpect(jsonPath("$.messageKey").value("validation.unique"));
  }

  @Test
  void should_translate_data_changed_exception_return_409() throws Exception {
    // given a request to an endpoint that throws a jOOQ DataChangedException (Optimistic Locking)

    // when
    var response = mockMvc.perform(get("/dummy/optimistic-lock-error").with(jwt()));

    // then
    response
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.messageKey").value("optimistic.locking"));
  }

  @Test
  void should_translate_unexpected_exception_return_500() throws Exception {
    // given a request to an endpoint that throws an unhandled RuntimeException

    // when
    var response = mockMvc.perform(get("/dummy/unexpected-error").with(jwt()));

    // then
    response
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.field").doesNotExist())
        .andExpect(
            jsonPath("$.params.errorId").exists()) // Ensures the UUID is generated for tracing
        .andExpect(jsonPath("$.params.errorId").isString());
  }

  @Test
  void should_handle_404_no_resource_found() throws Exception {
    // when: We request an endpoint that does not exist at all
    mockMvc
        .perform(get("/dummy/this-does-not-exist").with(jwt()))
        // then: Spring resolves it to 404 Not Found, and our advice intercepts it
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_translate_jakarta_validation_and_extract_params_return_422() throws Exception {
    // given: A payload that violates the @Size(min=2) constraint on our DummyDto
    String invalidJson =
        """
        { "name": "A" }
        """;

    // when / then: Spring catches the violation, the advice extracts min/max into the params map
    mockMvc
        .perform(
            post("/dummy/validate")
                .with(jwt().authorities(new SimpleGrantedAuthority("api:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[?(@.field == 'name')].field").value("name"))
        .andExpect(jsonPath("$[?(@.field == 'name')].actualValue").value("A"))
        .andExpect(jsonPath("$[?(@.field == 'name')].messageKey").value("size.invalid"))
        .andExpect(jsonPath("$[?(@.field == 'name')].params.min").value(2))
        .andExpect(jsonPath("$[?(@.field == 'name')].params.max").value(5))
        .andExpect(jsonPath("$[?(@.field == 'id')].field").value("id"))
        .andExpect(jsonPath("$[?(@.field == 'id')].actualValue").value((Object) null))
        .andExpect(jsonPath("$[?(@.field == 'id')].messageKey").value("required"))
        .andExpect(jsonPath("$[?(@.field == 'id')].params").value((Object) Map.of()));
  }

  @Test
  void should_translate_jakarta_global_validation_error_return_422() throws Exception {
    // given: A payload that passes field validation but fails class-level validation
    String validJson =
        """
        { "data": "Something" }
        """;

    // when / then: Spring registers an ObjectError, and the advice drops the 'field' attribute
    mockMvc
        .perform(
            post("/dummy/validate-global")
                .with(jwt().authorities(new SimpleGrantedAuthority("api:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].field").doesNotExist()) // Proves it is a global/form-level error
        .andExpect(jsonPath("$[0].actualValue").doesNotExist())
        .andExpect(jsonPath("$[0].messageKey").value("form.invalid"));
  }

  // --- Fakes to test the Advice ---
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = FakeGlobalValidator.class)
  public @interface ValidDummyForm {
    String message() default "form.invalid";

    Class<?>[] groups() default {};

    Class<? extends jakarta.validation.Payload>[] payload() default {};
  }

  // --- Fake Validator (Always fails) ---
  public static class FakeGlobalValidator
      implements jakarta.validation.ConstraintValidator<ValidDummyForm, GlobalDummyDto> {
    @Override
    public boolean isValid(
        GlobalDummyDto dto, jakarta.validation.ConstraintValidatorContext context) {
      return false; // Force a class-level ObjectError
    }
  }

  // --- The DTO with class-level constraint ---
  @ValidDummyForm
  public record GlobalDummyDto(String data) {}

  // --- The DTO with field-level constraints ---
  public record DummyDto(
      @NotNull(message = "required") Long id,
      @Size(min = 2, max = 5, message = "size.invalid") String name) {}

  /**
   * A fake controller used exclusively by this test class to trigger our exception handlers.
   * This keeps our error contract tests decoupled from real business controllers.
   */
  @RestController
  static class DummyController {

    @GetMapping("/dummy/object-error")
    public void throwObjectError() {
      throw new ObjectBusinessValidationException("object.invalid", Map.of());
    }

    @GetMapping("/dummy/field-error")
    public void throwFieldError() {
      throw new FieldBusinessValidationException("code", "DUP-01", "validation.unique", Map.of());
    }

    @GetMapping("/dummy/optimistic-lock-error")
    public void throwOptimisticLockError() {
      throw new DataChangedException("Concurrent update simulated");
    }

    @GetMapping("/dummy/unexpected-error")
    public void throwUnexpectedError() {
      throw new RuntimeException("Simulated catastrophic failure, like a DB timeout");
    }

    @PostMapping("/dummy/validate")
    public void validateInput(@Valid @RequestBody DummyDto dto) {
      // Do nothing, Spring's validation intercepts this before the body executes
    }

    @PostMapping("/dummy/validate-global")
    public void validateGlobalInput(@Valid @RequestBody GlobalDummyDto dto) {
      // Do nothing, Spring's validation intercepts this and throws the ObjectError
    }
  }
}
