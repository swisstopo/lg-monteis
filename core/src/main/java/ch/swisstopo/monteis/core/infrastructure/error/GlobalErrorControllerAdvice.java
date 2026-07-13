package ch.swisstopo.monteis.core.infrastructure.error;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// TODO: add optimistic locking.

/**
 * Global exception handler responsible for translating backend exceptions and
 * validation failures into the API error contract represented by {@link ErrorDto}.
 *
 * <p>This advice acts as the boundary between application/domain errors and the
 * HTTP layer. It converts different validation sources into a consistent error
 * representation:
 *
 * <ul>
 *   <li>{@link ObjectBusinessValidationException} is mapped to a form-level
 *       error because the violation affects the validity of an object as a
 *       whole.</li>
 *   <li>{@link FieldBusinessValidationException} is mapped to a field-level
 *       error because the violation can be associated with a specific input
 *       value.</li>
 *   <li>Jakarta Bean Validation errors are mapped based on whether they are
 *       field or object constraint violations.</li>
 *   <li>Unexpected technical failures are mapped to global errors and receive
 *       a correlation identifier for troubleshooting.</li>
 * </ul>
 *
 * <p>The resulting {@link ErrorDto} structure provides a stable contract for
 * clients regardless of whether the error originated from domain validation,
 * request validation, or infrastructure failures.
 */
@RestControllerAdvice
public class GlobalErrorControllerAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalErrorControllerAdvice.class);
  private static final Set<String> INTERNAL_ANNOTATION_KEYS =
      Set.of("message", "groups", "payload");

  @ExceptionHandler(ObjectBusinessValidationException.class)
  @ApiResponse(
      responseCode = "422",
      description = "Business validation failure across object fields.",
      content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  public ResponseEntity<ErrorDto> handleObjectBusinessValidationException(
      ObjectBusinessValidationException e) {
    ErrorDto payload = ErrorDto.form(e.getMessageKey(), e.getParams());

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(payload);
  }

  @ExceptionHandler(FieldBusinessValidationException.class)
  @ApiResponse(
      responseCode = "422",
      description = "Business validation failure on object field.",
      content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  public ResponseEntity<ErrorDto> handleFieldBusinessValidationException(
      FieldBusinessValidationException e) {
    ErrorDto payload =
        ErrorDto.field(e.getField(), e.getActualValue(), e.getMessageKey(), e.getParams());

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(payload);
  }

  @ApiResponse(
      responseCode = "422",
      description = "Business validation failure on User Input",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = ErrorDto.class))))
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    List<ErrorDto> errors =
        ex.getBindingResult().getAllErrors().stream().map(this::mapToErrorDto).toList();

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errors);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode statusCode,
      @NonNull WebRequest request) {

    String errorId = UUID.randomUUID().toString();
    log.warn("Spring MVC Framework Error [ErrorID: {}]: {}", errorId, ex.getMessage());
    ErrorDto payload = ErrorDto.global(Map.of("errorId", errorId));

    return ResponseEntity.status(statusCode).headers(headers).body(payload);
  }

  @ExceptionHandler(Exception.class)
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected Failure on Backend.",
      content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  public ResponseEntity<ErrorDto> handleAllOtherExceptions(
      Exception e, HttpServletRequest request) {
    String errorId = UUID.randomUUID().toString();
    String method = request.getMethod();
    String uri = request.getRequestURI();

    log.error("Unexpected system error at {} {} [ErrorID: {}]", method, uri, errorId, e);

    ErrorDto payload = ErrorDto.global(Map.of("errorId", errorId));

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
  }

  private ErrorDto mapToErrorDto(ObjectError error) {
    String messageKey = error.getDefaultMessage();

    Map<String, Object> params = extractConstraintParams(error);

    if (error instanceof FieldError fieldError) {
      String field = fieldError.getField();
      Object actualValue = fieldError.getRejectedValue();
      return ErrorDto.field(field, actualValue, messageKey, params);
    }

    return ErrorDto.form(messageKey, params);
  }

  /**
   * Extracts annotation attributes from Bean Validation constraints so clients
   * can render parameterized messages.
   *
   * <p>For example, {@code @Size(min = 3, max = 20)} becomes:
   * {@code {"min": 3, "max": 20}}.
   *
   * @param error validation error containing the constraint metadata
   * @return constraint parameters or an empty map if metadata cannot be extracted
   */
  private Map<String, Object> extractConstraintParams(ObjectError error) {

    try {

      ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);

      return violation.getConstraintDescriptor().getAttributes().entrySet().stream()
          .filter(e -> !INTERNAL_ANNOTATION_KEYS.contains(e.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }
}
