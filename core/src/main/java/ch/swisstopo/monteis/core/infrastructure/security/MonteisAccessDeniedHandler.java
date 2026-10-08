package ch.swisstopo.monteis.core.infrastructure.security;

import ch.swisstopo.monteis.core.infrastructure.error.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders a filter-chain denial as 403 with {@link ErrorDto#accessDenied()}, the same body
 * the RLS (42501) path produces in {@code GlobalErrorControllerAdvice} (contract C5). Without it,
 * Spring Security's default response has no {@code ErrorDto} body.
 *
 * <p>Logs one WARN line per denial with method, path and the caller's {@code sub} only - never the
 * token, its claims or experiment id lists (NFR2.5).
 */
class MonteisAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(MonteisAccessDeniedHandler.class);

  private final ObjectMapper objectMapper;

  MonteisAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.warn(
        "Access denied: {} {} for sub {}",
        request.getMethod(),
        request.getRequestURI(),
        callerSubject());

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ErrorDto.accessDenied());
  }

  private static String callerSubject() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
            && authentication.getPrincipal() instanceof MonteisPrincipal principal
        ? String.valueOf(principal.getSubject())
        : "anonymous";
  }
}
