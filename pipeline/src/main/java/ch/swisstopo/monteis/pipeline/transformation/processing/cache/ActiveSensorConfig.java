package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.scijava.parsington.ExpressionParser;
import org.scijava.parsington.SyntaxTree;
import org.scijava.parsington.eval.DefaultTreeEvaluator;

public class ActiveSensorConfig {

  private final SensorConfig config;
  private final SyntaxTree preParsedTree;

  public ActiveSensorConfig(SensorConfig config) {
    this.config = config;
    this.preParsedTree = new ExpressionParser().parseTree(config.getFormula());
  }

  public SensorConfig getConfig() {
    return config;
  }

  /**
   * Executes the pre-parsed formula.
   */
  public Double evaluate(Double rawValue) {
    DefaultTreeEvaluator evaluator = new DefaultTreeEvaluator();
    evaluator.set("x", rawValue);

    Object result = evaluator.evaluate(preParsedTree);

    return ((Number) result).doubleValue();
  }
}
