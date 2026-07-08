package ch.swisstopo.monteis.core.infrastructure.error;

import ch.swisstopo.monteis.core.infrastructure.exception.BusinessValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalErrorControllerAdvice {
  private static final Logger log = LoggerFactory.getLogger(GlobalErrorControllerAdvice.class);

  @ExceptionHandler(BusinessValidationException.class)
  public ResponseEntity<ErrorDTO> handleBusinessValidationException(BusinessValidationException e) {
    ErrorDTO payload =
        new ErrorDTO(e.getField(), e.getActualValue(), e.getMessageKey(), e.getParams());

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(payload);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDTO> handleException(Exception e, HttpServletRequest request) {
    String errorId = UUID.randomUUID().toString();

    String method = request.getMethod();
    String uri = request.getRequestURI();

    log.error("Unexpected system error at {} {} [ErrorID: {}]", method, uri, errorId, e);

    // 2. Return a safe, generic ErrorDTO to the frontend
    ErrorDTO payload =
        new ErrorDTO("global", null, "error.system.internal", Map.of("errorId", errorId));

    // 3. Return 500
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
  }
}
