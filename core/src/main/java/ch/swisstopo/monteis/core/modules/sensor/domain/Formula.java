package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.mapstruct.Default;
import java.util.Map;

public class Formula {
  private Long id;
  private String expression;
  private Integer version;
  private static final String DEFAULT_EXPRESSION = "x";

  /**
   * Constructor for creating a NEW Formula from a web request.
   * ID and Version are null because they haven't been assigned by the database yet.
   */
  @Default
  public Formula(String expression) {
    validateExpression(expression);
    this.expression = expression;
  }

  /**
   * Constructor for REBUILDING an existing Formula from the database (jOOQ).
   */
  public Formula(Long id, String expression, Integer version) {
    validateExpression(expression);
    this.id = id;
    this.expression = expression;
    this.version = version;
  }

  public Formula() {
    this.expression = DEFAULT_EXPRESSION; // Default fallback initialization
  }

  private void validateExpression(String expr) {
    if (expr == null || !expr.contains(DEFAULT_EXPRESSION)) {
      throw new FieldBusinessValidationException(
          "formulaControl", // should match frontend control
          expr,
          "validation.formula.malformed",
          Map.of("requiredVariable", "x"));
    }
  }

  // --- Getters and Setters ---

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public static FormulaBuilder builder() {
    return new FormulaBuilder();
  }

  public static class FormulaBuilder {
    private Long id;
    private String expression;
    private Integer version;

    public FormulaBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public FormulaBuilder expression(String expression) {
      this.expression = expression;
      return this;
    }

    public FormulaBuilder version(Integer version) {
      this.version = version;
      return this;
    }

    public Formula build() {
      return new Formula(this.id, this.expression, this.version);
    }
  }
}
