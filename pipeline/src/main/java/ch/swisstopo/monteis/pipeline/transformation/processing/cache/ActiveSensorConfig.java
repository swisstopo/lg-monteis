package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.persistence.ConfigStamp;
import org.scijava.parsington.ExpressionParser;
import org.scijava.parsington.SyntaxTree;
import org.scijava.parsington.Variable;
import org.scijava.parsington.eval.DefaultTreeEvaluator;

public class ActiveSensorConfig {

  private final SensorParameterConfig config;
  private final SyntaxTree preParsedTree;

  private static final String X_VARIABLE = "x";

  public ActiveSensorConfig(SensorParameterConfig config) {
    this.config = config;
    this.preParsedTree = new ExpressionParser().parseTree(config.getFormula());
  }

  public SensorParameterConfig getConfig() {
    return config;
  }

  // composed on call, not in the constructor: SensorConfigCache's default config has no DAS
  public String dasKey() {
    return DasKey.compose(
        config.getDas(), config.getDasSensorAlias(), config.getDasParameterAlias());
  }

  public ConfigStamp stamp() {
    return new ConfigStamp(
        dasKey(), config.getSensorParameterId(), config.getVersion().shortValue());
  }

  /**
   * Executes the pre-parsed formula.
   *
   * @param rawValue: the value to set for variable "x"
   * @return Returns the result when evaluating the formula.
   */
  public Double evaluate(Double rawValue) {
    DefaultTreeEvaluator evaluator = new DefaultTreeEvaluator();
    evaluator.set(X_VARIABLE, rawValue);

    Object result = evaluator.evaluate(preParsedTree);

    if (result instanceof Number validNumber && !Double.isInfinite(validNumber.doubleValue())) {
      return validNumber.doubleValue();
    }

    if (result instanceof Variable variable && X_VARIABLE.equals(variable.getToken())) {
      return rawValue;
    }

    // Poison Pill protection (User wrote "x + y" or similar invalid syntax)
    throw new IllegalArgumentException(
        "Formula did not evaluate to a clean numeric value. Unresolved variables might exist."
            + " Result was: "
            + result);
  }
}
