package ch.swisstopo.monteis.core.infrastructure.exception;

import java.util.Map;

public class BusinessValidationException extends RuntimeException {
  private final String field;
  private final Object actualValue;
  private final String messageKey;
  private final Map<String, Object> params;

  public BusinessValidationException(
      String field, Object actualValue, String messageKey, Map<String, Object> params) {
    // Pass the messageKey to the superclass so it appears in standard server logs
    super(messageKey);
    this.field = field;
    this.actualValue = actualValue;
    this.messageKey = messageKey;
    this.params = params != null ? params : Map.of();
  }

  public String getField() {
    return field;
  }

  public Object getActualValue() {
    return actualValue;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public Map<String, Object> getParams() {
    return params;
  }
}
